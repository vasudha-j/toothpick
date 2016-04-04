package toothpick.compiler.memberinjector;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import javax.lang.model.element.Modifier;
import toothpick.Factory;
import toothpick.Injector;
import toothpick.MemberInjector;
import toothpick.compiler.CodeGenerator;
import toothpick.compiler.factory.FactoryInjectionTarget;

/**
 * Generates a {@link MemberInjector} for a given collection of {@link MemberInjectorInjectionTarget}.
 * Typically a {@link MemberInjector} is created for a class a soon as it contains
 * an {@link javax.inject.Inject} annotated field.
 * TODO also deal with injected methods.
 */
public class MemberInjectorGenerator implements CodeGenerator {

  private static final String MEMBER_INJECTOR_SUFFIX = "$$MemberInjector";

  private List<MemberInjectorInjectionTarget> memberInjectorInjectionTargetList;

  public MemberInjectorGenerator(List<MemberInjectorInjectionTarget> memberInjectorInjectionTargetList) {
    this.memberInjectorInjectionTargetList = memberInjectorInjectionTargetList;
    if (memberInjectorInjectionTargetList.size() < 1) {
      throw new IllegalStateException("At least one memberInjectorInjectionTarget is needed.");
    }
  }

  public String brewJava() {
    // Interface to implement
    MemberInjectorInjectionTarget memberInjectorInjectionTarget = memberInjectorInjectionTargetList.get(0);
    ClassName className = ClassName.get(memberInjectorInjectionTarget.targetClassPackage, memberInjectorInjectionTarget.targetClassName);
    ParameterizedTypeName memberInjectorInterfaceParameterizedTypeName = ParameterizedTypeName.get(ClassName.get(MemberInjector.class), className);

    // Build class
    TypeSpec.Builder injectorMemberTypeSpec = TypeSpec.classBuilder(memberInjectorInjectionTarget.targetClassName + MEMBER_INJECTOR_SUFFIX)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addSuperinterface(memberInjectorInterfaceParameterizedTypeName);
    emitSuperMemberInjectorFieldIfNeeded(injectorMemberTypeSpec, memberInjectorInjectionTarget);
    emitInjectMethod(injectorMemberTypeSpec, memberInjectorInjectionTargetList);

    JavaFile javaFile = JavaFile.builder(memberInjectorInjectionTarget.targetClassPackage, injectorMemberTypeSpec.build())
        .addFileComment("Generated code from ToothPick. Do not modify!")
        .build();
    return javaFile.toString();
  }

  private void emitSuperMemberInjectorFieldIfNeeded(TypeSpec.Builder injectorMemberTypeSpec,
      MemberInjectorInjectionTarget memberInjectorInjectionTarget) {
    if (memberInjectorInjectionTarget.superClassThatNeedsInjectionClassName != null) {
      ClassName superTypeThatNeedsInjection = ClassName.get(memberInjectorInjectionTarget.superClassThatNeedsInjectionClassPackage,
          memberInjectorInjectionTarget.superClassThatNeedsInjectionClassName);
      ParameterizedTypeName memberInjectorSuperParameterizedTypeName =
          ParameterizedTypeName.get(ClassName.get(MemberInjector.class), superTypeThatNeedsInjection);
      FieldSpec.Builder superMemberInjectorField =
          FieldSpec.builder(memberInjectorSuperParameterizedTypeName, "superMemberInjector", Modifier.PRIVATE)
              //TODO use proper typing here
              .initializer("toothpick.registries.memberinjector.MemberInjectorRegistryLocator.getMemberInjector($L.class)",
                  memberInjectorInjectionTarget.superClassThatNeedsInjectionClassName);
      injectorMemberTypeSpec.addField(superMemberInjectorField.build());
    }
  }

  private void emitInjectMethod(TypeSpec.Builder injectorMemberTypeSpec, List<MemberInjectorInjectionTarget> memberInjectorInjectionTargetList) {
    MemberInjectorInjectionTarget memberInjectorInjectionTarget = memberInjectorInjectionTargetList.get(0);
    MethodSpec.Builder injectBuilder = MethodSpec.methodBuilder("inject")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ClassName.get(memberInjectorInjectionTarget.targetClassPackage, memberInjectorInjectionTarget.targetClassName), "target")
        .addParameter(ClassName.get(Injector.class), "injector");

    for (MemberInjectorInjectionTarget injectorInjectionTarget : memberInjectorInjectionTargetList) {
      final String injectorGetMethodName;
      final ClassName className;
      switch (injectorInjectionTarget.kind) {
        case INSTANCE:
          injectorGetMethodName = " = injector.getInstance(";
          className = ClassName.get(injectorInjectionTarget.memberClassPackage, injectorInjectionTarget.memberClassName);
          break;
        case PROVIDER:
          injectorGetMethodName = " = injector.getProvider(";
          className = ClassName.get(injectorInjectionTarget.kindParamPackageName, injectorInjectionTarget.kindParamClassName);
          break;
        case LAZY:
          injectorGetMethodName = " = injector.getLazy(";
          className = ClassName.get(injectorInjectionTarget.kindParamPackageName, injectorInjectionTarget.kindParamClassName);
          break;
        case FUTURE:
          injectorGetMethodName = " = injector.getFuture(";
          className = ClassName.get(injectorInjectionTarget.kindParamPackageName, injectorInjectionTarget.kindParamClassName);
          break;
        default:
          throw new IllegalStateException("The kind can't be null.");
      }
      StringBuilder assignFieldStatement;
      assignFieldStatement = new StringBuilder("target.");
      assignFieldStatement.append(injectorInjectionTarget.memberName).append(injectorGetMethodName).append(className).append(".class)");
      injectBuilder.addStatement(assignFieldStatement.toString());
    }

    if (memberInjectorInjectionTarget.superClassThatNeedsInjectionClassName != null) {
      injectBuilder.addStatement("superMemberInjector.inject(target, injector)");
    }

    injectorMemberTypeSpec.addMethod(injectBuilder.build());
  }

  @Override
  public String getFqcn() {
    MemberInjectorInjectionTarget firstMemberInjector = memberInjectorInjectionTargetList.get(0);
    return firstMemberInjector.targetClassPackage + "." + firstMemberInjector.targetClassName + MEMBER_INJECTOR_SUFFIX;
  }
}