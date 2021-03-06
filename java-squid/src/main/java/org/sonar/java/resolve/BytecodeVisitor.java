/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.resolve;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import javax.annotation.Nullable;
import java.util.List;

public class BytecodeVisitor extends ClassVisitor {

  private BytecodeCompleter bytecodeCompleter;
  private final Symbols symbols;
  private final Symbol.TypeSymbol classSymbol;

  /**
   * Name of current class in a format as it appears in bytecode, i.e. "org/example/MyClass$InnerClass".
   */
  private String className;

  BytecodeVisitor(BytecodeCompleter bytecodeCompleter, Symbols symbols, Symbol.TypeSymbol classSymbol) {
    super(Opcodes.ASM5);
    this.bytecodeCompleter = bytecodeCompleter;
    this.symbols = symbols;
    this.classSymbol = classSymbol;
  }

  private Symbol.TypeSymbol getClassSymbol(String bytecodeName) {
    return bytecodeCompleter.getClassSymbol(Convert.flatName(bytecodeName));
  }

  private Symbol.TypeSymbol getClassSymbol(String bytecodeName, int flags) {
    return bytecodeCompleter.getClassSymbol(Convert.flatName(bytecodeName), flags);
  }

  @Override
  public void visit(int version, int flags, String name, @Nullable String signature, @Nullable String superName, @Nullable String[] interfaces) {
    Preconditions.checkState(name.endsWith(classSymbol.name), "Name : '" + name + "' should ends with " + classSymbol.name);
    Preconditions.checkState(!BytecodeCompleter.isSynthetic(flags), name + " is synthetic");
    className = name;
    if(signature != null) {
      new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM5) {

        @Override
        public void visitFormalTypeParameter(String name) {
          //TODO improve generics
          classSymbol.isParametrized = true;
        }
      });
    }
    //if class has already access flags set (inner class) then do not reset those.
    //The important access flags are the one defined in the outer class.
    if ((classSymbol.flags & Flags.ACCESS_FLAGS) != 0) {
      classSymbol.flags |= bytecodeCompleter.filterBytecodeFlags(flags & ~Flags.ACCESS_FLAGS);
    } else {
      classSymbol.flags |= bytecodeCompleter.filterBytecodeFlags(flags);
    }
    classSymbol.members = new Scope(classSymbol);
    if (superName == null) {
      Preconditions.checkState("java/lang/Object".equals(className), "superName must be null only for java/lang/Object, but not for " + className);
      // TODO(Godin): what about interfaces and annotations
    } else {
      ((Type.ClassType) classSymbol.type).supertype = getClassSymbol(superName).type;
    }
    ((Type.ClassType) classSymbol.type).interfaces = getCompletedClassSymbolsType(interfaces);
  }

  @Override
  public void visitSource(@Nullable String source, @Nullable String debug) {
    throw new IllegalStateException();
  }

  /**
   * {@inheritDoc}
   * <p/>
   * In other words must be called only for anonymous classes or named classes declared within methods,
   * which must not be processed by {@link org.sonar.java.resolve.BytecodeCompleter}, therefore this method always throws {@link IllegalStateException}.
   *
   * @throws IllegalStateException always
   */
  @Override
  public void visitOuterClass(String owner, String name, String desc) {
    throw new IllegalStateException();
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    Type annotationType = convertAsmType(org.objectweb.asm.Type.getType(desc));
    AnnotationInstance annotationInstance = new AnnotationInstance(annotationType.getSymbol());
    classSymbol.metadata().addAnnotation(annotationInstance);
    return new BytecodeAnnotationVisitor(annotationInstance, this);
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
    // (Godin): can return AnnotationVisitor to read annotations
    return null;
  }

  @Override
  public void visitAttribute(Attribute attr) {
    // skip non standard attributes
  }

  @Override
  public void visitInnerClass(String name, @Nullable String outerName, @Nullable String innerName, int flags) {
    if (!BytecodeCompleter.isSynthetic(flags)) {
      // TODO what about flags?
      if (innerName == null) {
        // anonymous class
      } else if (outerName == null) {
        // named class declared within method
      } else if (className.equals(outerName)) {
        defineInnerClass(name, flags);
      } else if (className.equals(name)) {
        defineOuterClass(outerName, innerName, flags);
      } else {
        // FIXME(Godin): for example if loading started from "C1.C2.C3" in case of
        // class C1 { class C2 { class C3 { } } }
        // then name="C1$C2", outerName="C1" and innerName="C3"
      }
    }
  }

  /**
   * Invoked when current class classified as outer class of some inner class.
   * Adds inner class as member.
   */
  private void defineInnerClass(String bytecodeName, int flags) {
    Symbol.TypeSymbol innerClass = getClassSymbol(bytecodeName, flags);
    innerClass.flags |= bytecodeCompleter.filterBytecodeFlags(flags);
    Preconditions.checkState(innerClass.owner == classSymbol, "Innerclass: " + innerClass.owner.getName() + " and classSymbol: " + classSymbol.getName() + " are not the same.");
    classSymbol.members.enter(innerClass);
  }

  /**
   * Invoked when current class classified as inner class.
   * Owner of inner classes - is some outer class,
   * which is either already completed, and thus already has this inner class as member,
   * either will be completed by {@link org.sonar.java.resolve.BytecodeCompleter}, and thus will have this inner class as member (see {@link #defineInnerClass(String, int)}).
   */
  private void defineOuterClass(String outerName, String innerName, int flags) {
    Symbol.TypeSymbol outerClassSymbol = getClassSymbol(outerName, flags);
    Preconditions.checkState(outerClassSymbol.completer == null || outerClassSymbol.completer instanceof BytecodeCompleter);
    classSymbol.name = innerName;
    classSymbol.owner = outerClassSymbol;
  }

  @Override
  public FieldVisitor visitField(int flags, String name, String desc, @Nullable String signature, @Nullable Object value) {
    Preconditions.checkNotNull(name);
    Preconditions.checkNotNull(desc);
    if (!BytecodeCompleter.isSynthetic(flags)) {
      //Flags from asm lib are defined in Opcodes class and map to flags defined in Flags class
      final Symbol.VariableSymbol symbol = new Symbol.VariableSymbol(bytecodeCompleter.filterBytecodeFlags(flags),
          name, convertAsmType(org.objectweb.asm.Type.getType(desc)), classSymbol);
      classSymbol.members.enter(symbol);
      if(signature != null) {
        new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM5) {

          @Override
          public void visitFormalTypeParameter(String name) {
            //TODO improve generics
            symbol.isParametrized = true;
          }
        });
      }
    }
    // (Godin): can return FieldVisitor to read annotations
    return null;
  }

  @Override
  public MethodVisitor visitMethod(int flags, String name, String desc, @Nullable String signature, @Nullable String[] exceptions) {
    Preconditions.checkNotNull(name);
    Preconditions.checkNotNull(desc);
    if (!BytecodeCompleter.isSynthetic(flags)) {
      Preconditions.checkState((flags & Opcodes.ACC_BRIDGE) == 0, "bridge method not marked as synthetic in class " + className);
      // TODO(Godin): according to JVMS 4.7.24 - parameter can be marked as synthetic
      Type.MethodType type = new Type.MethodType(
          convertAsmTypes(org.objectweb.asm.Type.getArgumentTypes(desc)),
          convertAsmType(org.objectweb.asm.Type.getReturnType(desc)),
          getCompletedClassSymbolsType(exceptions),
          classSymbol
      );
      final Symbol.MethodSymbol methodSymbol = new Symbol.MethodSymbol(bytecodeCompleter.filterBytecodeFlags(flags), name, type, classSymbol);
      classSymbol.members.enter(methodSymbol);
      if(signature != null) {
        new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM5) {

          @Override
          public void visitFormalTypeParameter(String name) {
            //TODO improve generics
            methodSymbol.isParametrized = true;
          }
        });
      }
    }
    // (Godin): can return MethodVisitor to read annotations
    return null;
  }

  private List<Type> convertAsmTypes(org.objectweb.asm.Type[] asmTypes) {
    ImmutableList.Builder<Type> result = ImmutableList.builder();
    for (org.objectweb.asm.Type asmType : asmTypes) {
      result.add(convertAsmType(asmType));
    }
    return result.build();
  }

  public Type convertAsmType(org.objectweb.asm.Type asmType) {
    Type result;
    switch (asmType.getSort()) {
      case org.objectweb.asm.Type.OBJECT:
        result = getClassSymbol(asmType.getInternalName()).type;
        break;
      case org.objectweb.asm.Type.BYTE:
        result = symbols.byteType;
        break;
      case org.objectweb.asm.Type.CHAR:
        result = symbols.charType;
        break;
      case org.objectweb.asm.Type.SHORT:
        result = symbols.shortType;
        break;
      case org.objectweb.asm.Type.INT:
        result = symbols.intType;
        break;
      case org.objectweb.asm.Type.LONG:
        result = symbols.longType;
        break;
      case org.objectweb.asm.Type.FLOAT:
        result = symbols.floatType;
        break;
      case org.objectweb.asm.Type.DOUBLE:
        result = symbols.doubleType;
        break;
      case org.objectweb.asm.Type.BOOLEAN:
        result = symbols.booleanType;
        break;
      case org.objectweb.asm.Type.ARRAY:
        result = new Type.ArrayType(convertAsmType(asmType.getElementType()), symbols.arrayClass);
        break;
      case org.objectweb.asm.Type.VOID:
        result = symbols.voidType;
        break;
      default:
        throw new IllegalArgumentException(asmType.toString());
    }
    return result;
  }

  /**
   * If at this point there is no owner of current class, then this is a top-level class,
   * because outer classes always will be completed before inner classes - see {@link #defineOuterClass(String, String, int)}.
   * Owner of top-level classes - is a package.
   */
  @Override
  public void visitEnd() {
    if (classSymbol.owner == null) {
      String flatName = className.replace('/', '.');
      classSymbol.name = flatName.substring(flatName.lastIndexOf('.') + 1);
      classSymbol.owner = bytecodeCompleter.enterPackage(flatName);
      Symbol.PackageSymbol owner = (Symbol.PackageSymbol) classSymbol.owner;
      if (owner.members == null) {
        // package was without classes so far
        owner.members = new Scope(owner);
      }
      owner.members.enter(classSymbol);
    }
  }

  private List<Type> getCompletedClassSymbolsType(@Nullable String[] bytecodeNames) {
    if (bytecodeNames == null) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<Type> types = ImmutableList.builder();
    for (String bytecodeName : bytecodeNames) {
      types.add(getClassSymbol(bytecodeName).type);
    }
    return types.build();
  }

}
