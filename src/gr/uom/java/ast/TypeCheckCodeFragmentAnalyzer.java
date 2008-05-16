package gr.uom.java.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.tree.DefaultMutableTreeNode;

import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import gr.uom.java.ast.util.ExpressionExtractor;
import gr.uom.java.ast.util.MethodDeclarationUtility;
import gr.uom.java.ast.util.StatementExtractor;
import gr.uom.java.jdeodorant.refactoring.manipulators.TypeCheckElimination;

public class TypeCheckCodeFragmentAnalyzer {
	private TypeCheckElimination typeCheckElimination;
	private TypeDeclaration typeDeclaration;
	private MethodDeclaration typeCheckMethod;
	private FieldDeclaration[] fields;
	private MethodDeclaration[] methods;
	private Map<SimpleName, Integer> typeVariableCounterMap;
	private Map<MethodInvocation, Integer> typeMethodInvocationCounterMap;
	private Map<Expression, IfStatementExpressionAnalyzer> complexExpressionMap;
	
	public TypeCheckCodeFragmentAnalyzer(TypeCheckElimination typeCheckElimination,
			TypeDeclaration typeDeclaration, MethodDeclaration typeCheckMethod) {
		this.typeCheckElimination = typeCheckElimination;
		this.typeDeclaration = typeDeclaration;
		this.typeCheckMethod = typeCheckMethod;
		this.fields = typeDeclaration.getFields();
		this.methods = typeDeclaration.getMethods();
		this.typeVariableCounterMap = new LinkedHashMap<SimpleName, Integer>();
		this.typeMethodInvocationCounterMap = new LinkedHashMap<MethodInvocation, Integer>();
		this.complexExpressionMap = new LinkedHashMap<Expression, IfStatementExpressionAnalyzer>();
		typeCheckElimination.setTypeCheckClass(typeDeclaration);
		typeCheckElimination.setTypeCheckMethod(typeCheckMethod);
		processTypeCheckCodeFragment();
	}
	
	private void processTypeCheckCodeFragment() {
		List<ArrayList<Statement>> typeCheckStatements = typeCheckElimination.getTypeCheckStatements();
		ArrayList<Statement> firstBlockOfStatements = typeCheckStatements.get(0);
		Statement firstStatementOfBlock = firstBlockOfStatements.get(0);
		if(firstStatementOfBlock.getParent() instanceof SwitchStatement) {
			SwitchStatement switchStatement = (SwitchStatement)firstStatementOfBlock.getParent();
			Expression switchStatementExpression = switchStatement.getExpression();
			Expression switchStatementExpressionName = extractOperand(switchStatementExpression);
			if(switchStatementExpressionName != null) {
				if(switchStatementExpressionName instanceof SimpleName) {
					SimpleName switchStatementExpressionSimpleName = (SimpleName)switchStatementExpressionName;
					IBinding switchStatementExpressionNameBinding = switchStatementExpressionSimpleName.resolveBinding();
					if(switchStatementExpressionNameBinding.getKind() == IBinding.VARIABLE) {
						IVariableBinding switchStatementExpressionNameVariableBinding = (IVariableBinding)switchStatementExpressionNameBinding;
						if(switchStatementExpressionNameVariableBinding.isField()) {
							for(FieldDeclaration field : fields) {
								List<VariableDeclarationFragment> fragments = field.fragments();
								for(VariableDeclarationFragment fragment : fragments) {
									IVariableBinding fragmentVariableBinding = fragment.resolveBinding();
									if(fragmentVariableBinding.isEqualTo(switchStatementExpressionNameVariableBinding)) {
										typeCheckElimination.setTypeField(fragment);
										for(MethodDeclaration method : methods) {
											SimpleName fieldInstruction = MethodDeclarationUtility.isSetter(method);
											if(fieldInstruction != null && fragment.getName().getIdentifier().equals(fieldInstruction.getIdentifier())) {
												typeCheckElimination.setTypeFieldSetterMethod(method);
											}
											fieldInstruction = MethodDeclarationUtility.isGetter(method);
											if(fieldInstruction != null && fragment.getName().getIdentifier().equals(fieldInstruction.getIdentifier())) {
												typeCheckElimination.setTypeFieldGetterMethod(method);
											}
										}
										break;
									}
								}
							}
						}
						else if(switchStatementExpressionNameVariableBinding.isParameter()) {
							List<SingleVariableDeclaration> parameters = typeCheckMethod.parameters();
							for(SingleVariableDeclaration parameter : parameters) {
								IVariableBinding parameterVariableBinding = parameter.resolveBinding();
								if(parameterVariableBinding.isEqualTo(switchStatementExpressionNameVariableBinding)) {
									typeCheckElimination.setTypeLocalVariable(parameter);
									break;
								}
							}
						}
						else {
							StatementExtractor statementExtractor = new StatementExtractor();
							List<Statement> variableDeclarationStatements = statementExtractor.getVariableDeclarations(typeCheckMethod.getBody());
							for(Statement vDStatement : variableDeclarationStatements) {
								VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement)vDStatement;
								List<VariableDeclarationFragment> fragments = variableDeclarationStatement.fragments();
								for(VariableDeclarationFragment fragment : fragments) {
									IVariableBinding fragmentVariableBinding = fragment.resolveBinding();
									if(fragmentVariableBinding.isEqualTo(switchStatementExpressionNameVariableBinding)) {
										typeCheckElimination.setTypeLocalVariable(fragment);
										break;
									}
								}
							}
							List<Statement> enhancedForStatements = statementExtractor.getEnhancedForStatements(typeCheckMethod.getBody());
							for(Statement eFStatement : enhancedForStatements) {
								EnhancedForStatement enhancedForStatement = (EnhancedForStatement)eFStatement;
								SingleVariableDeclaration formalParameter = enhancedForStatement.getParameter();
								IVariableBinding parameterVariableBinding = formalParameter.resolveBinding();
								if(parameterVariableBinding.isEqualTo(switchStatementExpressionNameVariableBinding)) {
									typeCheckElimination.setTypeLocalVariable(formalParameter);
									break;
								}
							}
						}
					}
				}
				else if(switchStatementExpressionName instanceof MethodInvocation) {
					MethodInvocation switchStatementExpressionMethodInvocation = (MethodInvocation)switchStatementExpressionName;
					Expression invoker = switchStatementExpressionMethodInvocation.getExpression();
					IMethodBinding switchStatementExpressionMethodBinding = switchStatementExpressionMethodInvocation.resolveMethodBinding();
					if(!switchStatementExpressionMethodBinding.getDeclaringClass().isEqualTo(typeDeclaration.resolveBinding()) &&
							invoker != null && !(invoker instanceof ThisExpression)) {
						typeCheckElimination.setTypeMethodInvocation(switchStatementExpressionMethodInvocation);
					}
				}
			}
		}
		
		Set<Expression> typeCheckExpressions = typeCheckElimination.getTypeCheckExpressions();
		for(Expression typeCheckExpression : typeCheckExpressions) {
			if(typeCheckExpression.getParent() instanceof SwitchCase) {
				if(typeCheckExpression instanceof SimpleName) {
					SimpleName simpleName = (SimpleName)typeCheckExpression;
					IBinding binding = simpleName.resolveBinding();
					if(binding.getKind() == IBinding.VARIABLE) {
						IVariableBinding variableBinding = (IVariableBinding)binding;
						if(variableBinding.isField() && (variableBinding.getModifiers() & Modifier.STATIC) != 0) {
							typeCheckElimination.addStaticType(typeCheckExpression, simpleName);
						}
					}
				}
				else if(typeCheckExpression instanceof QualifiedName) {
					QualifiedName qualifiedName = (QualifiedName)typeCheckExpression;
					IBinding binding = qualifiedName.resolveBinding();
					if(binding.getKind() == IBinding.VARIABLE) {
						IVariableBinding variableBinding = (IVariableBinding)binding;
						if(variableBinding.isField() && (variableBinding.getModifiers() & Modifier.STATIC) != 0) {
							typeCheckElimination.addStaticType(typeCheckExpression, qualifiedName.getName());
						}
					}
				}
				else if(typeCheckExpression instanceof FieldAccess) {
					FieldAccess fieldAccess = (FieldAccess)typeCheckExpression;
					IVariableBinding variableBinding = fieldAccess.resolveFieldBinding();
					if(variableBinding.isField() && (variableBinding.getModifiers() & Modifier.STATIC) != 0) {
						typeCheckElimination.addStaticType(typeCheckExpression, fieldAccess.getName());
					}
				}
			}
			else if(typeCheckExpression instanceof InstanceofExpression) {
				InstanceofExpression instanceofExpression = (InstanceofExpression)typeCheckExpression;
				Expression operandExpression = extractOperand(instanceofExpression.getLeftOperand());
				if(operandExpression != null) {
					if(operandExpression instanceof SimpleName) {
						SimpleName operandName = (SimpleName)operandExpression;
						SimpleName keySimpleName = containsTypeVariableKey(operandName);
						if(keySimpleName != null) {
							typeVariableCounterMap.put(keySimpleName, typeVariableCounterMap.get(keySimpleName)+1);
						}
						else {
							typeVariableCounterMap.put(operandName, 1);
						}
					}
					else if(operandExpression instanceof MethodInvocation) {
						MethodInvocation operandMethodInvocation = (MethodInvocation)operandExpression;
						MethodInvocation keyMethodInvocation = containsTypeMethodInvocationKey(operandMethodInvocation);
						if(keyMethodInvocation != null) {
							typeMethodInvocationCounterMap.put(keyMethodInvocation, typeMethodInvocationCounterMap.get(keyMethodInvocation)+1);
						}
						else {
							typeMethodInvocationCounterMap.put(operandMethodInvocation, 1);
						}
					}
					typeCheckElimination.addSubclassType(typeCheckExpression, instanceofExpression.getRightOperand());
				}
			}
			else if(typeCheckExpression instanceof InfixExpression) {
				InfixExpression infixExpression = (InfixExpression)typeCheckExpression;
				IfStatementExpressionAnalyzer analyzer = new IfStatementExpressionAnalyzer(infixExpression);
				for(InfixExpression leafInfixExpression : analyzer.getInfixExpressionsWithEqualsOperator()) {
					Expression leftOperand = leafInfixExpression.getLeftOperand();
					Expression rightOperand = leafInfixExpression.getRightOperand();
					Expression leftOperandExpression = extractOperand(leftOperand);
					Expression rightOperandExpression = extractOperand(rightOperand);
					SimpleName typeVariableName = null;
					SimpleName staticFieldName = null;
					MethodInvocation typeMethodInvocation = null;
					Type subclassType = null;
					if(leftOperandExpression != null && rightOperandExpression != null) {
						if(leftOperandExpression instanceof SimpleName) {
							SimpleName leftOperandName = (SimpleName)leftOperandExpression;
							IBinding leftOperandNameBinding = leftOperandName.resolveBinding();
							if(leftOperandNameBinding.getKind() == IBinding.VARIABLE) {
								IVariableBinding leftOperandNameVariableBinding = (IVariableBinding)leftOperandNameBinding;
								if(leftOperandNameVariableBinding.isField() && (leftOperandNameVariableBinding.getModifiers() & Modifier.STATIC) != 0)
									staticFieldName = leftOperandName;
							}
						}
						if(rightOperandExpression instanceof SimpleName) {
							SimpleName rightOperandName = (SimpleName)rightOperandExpression;
							IBinding rightOperandNameBinding = rightOperandName.resolveBinding();
							if(rightOperandNameBinding.getKind() == IBinding.VARIABLE) {
								IVariableBinding rightOperandNameVariableBinding = (IVariableBinding)rightOperandNameBinding;
								if(rightOperandNameVariableBinding.isField() && (rightOperandNameVariableBinding.getModifiers() & Modifier.STATIC) != 0)
									staticFieldName = rightOperandName;
							}
						}
						if(staticFieldName != null && staticFieldName.equals(leftOperandExpression)) {
							if(rightOperandExpression instanceof SimpleName) {
								SimpleName rightOperandName = (SimpleName)rightOperandExpression;
								typeVariableName = rightOperandName;
							}
							else if(rightOperandExpression instanceof MethodInvocation) {
								MethodInvocation rightOperandMethodInvocation = (MethodInvocation)rightOperandExpression;
								typeMethodInvocation = rightOperandMethodInvocation;
							}
						}
						else if(staticFieldName != null && staticFieldName.equals(rightOperandExpression)) {
							if(leftOperandExpression instanceof SimpleName) {
								SimpleName leftOperandName = (SimpleName)leftOperandExpression;
								typeVariableName = leftOperandName;
							}
							else if(leftOperandExpression instanceof MethodInvocation) {
								MethodInvocation leftOperandMethodInvocation = (MethodInvocation)leftOperandExpression;
								typeMethodInvocation = leftOperandMethodInvocation;
							}
						}
					}
					else if(leftOperandExpression != null && rightOperandExpression == null) {
						if(rightOperand instanceof TypeLiteral) {
							TypeLiteral typeLiteral = (TypeLiteral)rightOperand;
							subclassType = typeLiteral.getType();
							if(leftOperandExpression instanceof SimpleName) {
								SimpleName leftOperandName = (SimpleName)leftOperandExpression;
								typeVariableName = leftOperandName;
							}
							else if(leftOperandExpression instanceof MethodInvocation) {
								MethodInvocation leftOperandMethodInvocation = (MethodInvocation)leftOperandExpression;
								typeMethodInvocation = leftOperandMethodInvocation;
							}
						}
					}
					else if(leftOperandExpression == null && rightOperandExpression != null) {
						if(leftOperand instanceof TypeLiteral) {
							TypeLiteral typeLiteral = (TypeLiteral)leftOperand;
							subclassType = typeLiteral.getType();
							if(rightOperandExpression instanceof SimpleName) {
								SimpleName rightOperandName = (SimpleName)rightOperandExpression;
								typeVariableName = rightOperandName;
							}
							else if(rightOperandExpression instanceof MethodInvocation) {
								MethodInvocation rightOperandMethodInvocation = (MethodInvocation)rightOperandExpression;
								typeMethodInvocation = rightOperandMethodInvocation;
							}
						}
					}
					if(typeVariableName != null && staticFieldName != null) {
						SimpleName keySimpleName = containsTypeVariableKey(typeVariableName);
						if(keySimpleName != null) {
							typeVariableCounterMap.put(keySimpleName, typeVariableCounterMap.get(keySimpleName)+1);
						}
						else {
							typeVariableCounterMap.put(typeVariableName, 1);
						}
						if(analyzer.allParentNodesAreConditionalAndOperators()) {
							analyzer.putTypeVariableExpression(typeVariableName, leafInfixExpression);
							analyzer.putTypeVariableStaticField(typeVariableName, staticFieldName);
						}
					}
					if(typeMethodInvocation != null && staticFieldName != null) {
						MethodInvocation keyMethodInvocation = containsTypeMethodInvocationKey(typeMethodInvocation);
						if(keyMethodInvocation != null) {
							typeMethodInvocationCounterMap.put(keyMethodInvocation, typeMethodInvocationCounterMap.get(keyMethodInvocation)+1);
						}
						else {
							typeMethodInvocationCounterMap.put(typeMethodInvocation, 1);
						}
						if(analyzer.allParentNodesAreConditionalAndOperators()) {
							analyzer.putTypeMethodInvocationExpression(typeMethodInvocation, leafInfixExpression);
							analyzer.putTypeMethodInvocationStaticField(typeMethodInvocation, staticFieldName);
						}
					}
					if(typeVariableName != null && subclassType != null) {
						SimpleName keySimpleName = containsTypeVariableKey(typeVariableName);
						if(keySimpleName != null) {
							typeVariableCounterMap.put(keySimpleName, typeVariableCounterMap.get(keySimpleName)+1);
						}
						else {
							typeVariableCounterMap.put(typeVariableName, 1);
						}
						if(analyzer.allParentNodesAreConditionalAndOperators()) {
							analyzer.putTypeVariableExpression(typeVariableName, leafInfixExpression);
							analyzer.putTypeVariableSubclass(typeVariableName, subclassType);
						}
					}
					if(typeMethodInvocation != null && subclassType != null) {
						MethodInvocation keyMethodInvocation = containsTypeMethodInvocationKey(typeMethodInvocation);
						if(keyMethodInvocation != null) {
							typeMethodInvocationCounterMap.put(keyMethodInvocation, typeMethodInvocationCounterMap.get(keyMethodInvocation)+1);
						}
						else {
							typeMethodInvocationCounterMap.put(typeMethodInvocation, 1);
						}
						if(analyzer.allParentNodesAreConditionalAndOperators()) {
							analyzer.putTypeMethodInvocationExpression(typeMethodInvocation, leafInfixExpression);
							analyzer.putTypeMethodInvocationSubclass(typeMethodInvocation, subclassType);
						}
					}
				}
				for(InstanceofExpression leafInstanceofExpression : analyzer.getInstanceofExpressions()) {
					Expression operandExpression = extractOperand(leafInstanceofExpression.getLeftOperand());
					if(operandExpression != null) {
						if(operandExpression instanceof SimpleName) {
							SimpleName operandName = (SimpleName)operandExpression;
							SimpleName keySimpleName = containsTypeVariableKey(operandName);
							if(keySimpleName != null) {
								typeVariableCounterMap.put(keySimpleName, typeVariableCounterMap.get(keySimpleName)+1);
							}
							else {
								typeVariableCounterMap.put(operandName, 1);
							}
							if(analyzer.allParentNodesAreConditionalAndOperators()) {
								analyzer.putTypeVariableExpression(operandName, leafInstanceofExpression);
								analyzer.putTypeVariableSubclass(operandName, leafInstanceofExpression.getRightOperand());
							}
						}
						else if(operandExpression instanceof MethodInvocation) {
							MethodInvocation operandMethodInvocation = (MethodInvocation)operandExpression;
							MethodInvocation keyMethodInvocation = containsTypeMethodInvocationKey(operandMethodInvocation);
							if(keyMethodInvocation != null) {
								typeMethodInvocationCounterMap.put(keyMethodInvocation, typeMethodInvocationCounterMap.get(keyMethodInvocation)+1);
							}
							else {
								typeMethodInvocationCounterMap.put(operandMethodInvocation, 1);
							}
							if(analyzer.allParentNodesAreConditionalAndOperators()) {
								analyzer.putTypeMethodInvocationExpression(operandMethodInvocation, leafInstanceofExpression);
								analyzer.putTypeMethodInvocationSubclass(operandMethodInvocation, leafInstanceofExpression.getRightOperand());
							}
						}
					}
				}
				complexExpressionMap.put(typeCheckExpression, analyzer);
			}
		}
		for(SimpleName typeVariable : typeVariableCounterMap.keySet()) {
			if(typeVariableCounterMap.get(typeVariable) == typeCheckExpressions.size()) {
				for(Expression complexExpression : complexExpressionMap.keySet()) {
					IfStatementExpressionAnalyzer analyzer = complexExpressionMap.get(complexExpression);
					for(SimpleName analyzerTypeVariable : analyzer.getTargetVariables()) {
						if(analyzerTypeVariable.resolveBinding().isEqualTo(typeVariable.resolveBinding())) {
							typeCheckElimination.addRemainingIfStatementExpression(analyzer.getCompleteExpression(),
									analyzer.getRemainingExpression(analyzer.getTypeVariableExpression(analyzerTypeVariable)));
							SimpleName staticField = analyzer.getTypeVariableStaticField(analyzerTypeVariable);
							if(staticField != null)
								typeCheckElimination.addStaticType(analyzer.getCompleteExpression(), staticField);
							Type subclassType = analyzer.getTypeVariableSubclass(analyzerTypeVariable);
							if(subclassType != null)
								typeCheckElimination.addSubclassType(analyzer.getCompleteExpression(), subclassType);
						}
					}
				}
				IBinding binding = typeVariable.resolveBinding();
				if(binding.getKind() == IBinding.VARIABLE) {
					IVariableBinding variableBinding = (IVariableBinding)binding;
					if(variableBinding.isField()) {
						for(FieldDeclaration field : fields) {
							List<VariableDeclarationFragment> fragments = field.fragments();
							for(VariableDeclarationFragment fragment : fragments) {
								IVariableBinding fragmentVariableBinding = fragment.resolveBinding();
								if(fragmentVariableBinding.isEqualTo(variableBinding)) {
									typeCheckElimination.setTypeField(fragment);
									for(MethodDeclaration method : methods) {
										SimpleName fieldInstruction = MethodDeclarationUtility.isSetter(method);
										if(fieldInstruction != null && fragment.getName().getIdentifier().equals(fieldInstruction.getIdentifier())) {
											typeCheckElimination.setTypeFieldSetterMethod(method);
										}
										fieldInstruction = MethodDeclarationUtility.isGetter(method);
										if(fieldInstruction != null && fragment.getName().getIdentifier().equals(fieldInstruction.getIdentifier())) {
											typeCheckElimination.setTypeFieldGetterMethod(method);
										}
									}
									break;
								}
							}
						}
					}
					else if(variableBinding.isParameter()) {
						List<SingleVariableDeclaration> parameters = typeCheckMethod.parameters();
						for(SingleVariableDeclaration parameter : parameters) {
							IVariableBinding parameterVariableBinding = parameter.resolveBinding();
							if(parameterVariableBinding.isEqualTo(variableBinding)) {
								typeCheckElimination.setTypeLocalVariable(parameter);
								break;
							}
						}
					}
					else {
						StatementExtractor statementExtractor = new StatementExtractor();
						List<Statement> variableDeclarationStatements = statementExtractor.getVariableDeclarations(typeCheckMethod.getBody());
						for(Statement vDStatement : variableDeclarationStatements) {
							VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement)vDStatement;
							List<VariableDeclarationFragment> fragments = variableDeclarationStatement.fragments();
							for(VariableDeclarationFragment fragment : fragments) {
								IVariableBinding fragmentVariableBinding = fragment.resolveBinding();
								if(fragmentVariableBinding.isEqualTo(variableBinding)) {
									typeCheckElimination.setTypeLocalVariable(fragment);
									break;
								}
							}
						}
						List<Statement> enhancedForStatements = statementExtractor.getEnhancedForStatements(typeCheckMethod.getBody());
						for(Statement eFStatement : enhancedForStatements) {
							EnhancedForStatement enhancedForStatement = (EnhancedForStatement)eFStatement;
							SingleVariableDeclaration formalParameter = enhancedForStatement.getParameter();
							IVariableBinding parameterVariableBinding = formalParameter.resolveBinding();
							if(parameterVariableBinding.isEqualTo(variableBinding)) {
								typeCheckElimination.setTypeLocalVariable(formalParameter);
								break;
							}
						}
					}
				}
			}
		}
		for(MethodInvocation typeMethodInvocation : typeMethodInvocationCounterMap.keySet()) {
			if(typeMethodInvocationCounterMap.get(typeMethodInvocation) == typeCheckExpressions.size()) {
				for(Expression complexExpression : complexExpressionMap.keySet()) {
					IfStatementExpressionAnalyzer analyzer = complexExpressionMap.get(complexExpression);
					for(MethodInvocation analyzerTypeMethodInvocation : analyzer.getTargetMethodInvocations()) {
						if(analyzerTypeMethodInvocation.resolveMethodBinding().isEqualTo(typeMethodInvocation.resolveMethodBinding())) {
							typeCheckElimination.addRemainingIfStatementExpression(analyzer.getCompleteExpression(),
									analyzer.getRemainingExpression(analyzer.getTypeMethodInvocationExpression(analyzerTypeMethodInvocation)));
							SimpleName staticField = analyzer.getTypeMethodInvocationStaticField(analyzerTypeMethodInvocation);
							if(staticField != null)
								typeCheckElimination.addStaticType(analyzer.getCompleteExpression(), staticField);
							Type subclassType = analyzer.getTypeMethodInvocationSubclass(analyzerTypeMethodInvocation);
							if(subclassType != null)
								typeCheckElimination.addSubclassType(analyzer.getCompleteExpression(), subclassType);
						}
					}
				}
				Expression invoker = typeMethodInvocation.getExpression();
				IMethodBinding typeMethodInvocationBinding = typeMethodInvocation.resolveMethodBinding();
				if(!typeMethodInvocationBinding.getDeclaringClass().isEqualTo(typeDeclaration.resolveBinding()) &&
						invoker != null && !(invoker instanceof ThisExpression)) {
					typeCheckElimination.setTypeMethodInvocation(typeMethodInvocation);
				}
			}
		}
		processTypeCheckCodeFragmentBranches();
	}
	
	private void processTypeCheckCodeFragmentBranches() {
		ExpressionExtractor expressionExtractor = new ExpressionExtractor();
		List<ArrayList<Statement>> allTypeCheckStatements = typeCheckElimination.getTypeCheckStatements();
		if(!typeCheckElimination.getDefaultCaseStatements().isEmpty()) {
			allTypeCheckStatements.add(typeCheckElimination.getDefaultCaseStatements());
		}
		StatementExtractor statementExtractor = new StatementExtractor();
		List<Statement> variableDeclarationStatementsInsideTypeCheckMethodApartFromTypeCheckCodeFragment = statementExtractor.getVariableDeclarations(typeCheckMethod.getBody());
		for(ArrayList<Statement> typeCheckStatementList : allTypeCheckStatements) {
			for(Statement statement : typeCheckStatementList) {
				variableDeclarationStatementsInsideTypeCheckMethodApartFromTypeCheckCodeFragment.removeAll(statementExtractor.getVariableDeclarations(statement));
			}
		}
		for(ArrayList<Statement> typeCheckStatementList : allTypeCheckStatements) {
			for(Statement statement : typeCheckStatementList) {
				//checking for methods of the Source class invoked inside the type-checking branches
				List<Expression> methodInvocations = expressionExtractor.getMethodInvocations(statement);
				for(Expression expression : methodInvocations) {
					if(expression instanceof MethodInvocation) {
						MethodInvocation methodInvocation = (MethodInvocation)expression;
						IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
						if(methodBinding.getDeclaringClass().isEqualTo(typeDeclaration.resolveBinding())) {
							for(MethodDeclaration method : methods) {
								if(method.resolveBinding().isEqualTo(methodBinding)) {
									typeCheckElimination.addAccessedMethod(method);
								}
							}
						}
						else if(methodInvocation.getExpression() == null || (methodInvocation.getExpression() != null && methodInvocation.getExpression() instanceof ThisExpression)) {
							ITypeBinding superclassTypeBinding = typeDeclaration.resolveBinding().getSuperclass();
							while(superclassTypeBinding != null && !superclassTypeBinding.isEqualTo(methodBinding.getDeclaringClass())) {
								superclassTypeBinding = superclassTypeBinding.getSuperclass();
							}
							if(methodBinding.getDeclaringClass().isEqualTo(superclassTypeBinding))
								typeCheckElimination.addSuperAccessedMethod(methodBinding);
						}
					}
				}
				//checking for Source class fields or parameters of the type-checking method accessed inside the type-checking branches
				List<Expression> variableInstructions = expressionExtractor.getVariableInstructions(statement);
				for(Expression variableInstruction : variableInstructions) {
					SimpleName simpleName = (SimpleName)variableInstruction;
					IBinding variableInstructionBinding = simpleName.resolveBinding();
					if(variableInstructionBinding.getKind() == IBinding.VARIABLE) {
						IVariableBinding variableInstructionVariableBinding = (IVariableBinding)variableInstructionBinding;
						if(variableInstructionVariableBinding.isField()) {
							if(variableInstructionVariableBinding.getDeclaringClass() != null && variableInstructionVariableBinding.getDeclaringClass().isEqualTo(typeDeclaration.resolveBinding())) {
								for(FieldDeclaration field : fields) {
									List<VariableDeclarationFragment> fragments = field.fragments();
									for(VariableDeclarationFragment fragment : fragments) {
										IVariableBinding fragmentVariableBinding = fragment.resolveBinding();
										if(fragmentVariableBinding.isEqualTo(variableInstructionVariableBinding)) {
											Expression parentExpression = null;
											if(simpleName.getParent() instanceof QualifiedName) {
												parentExpression = (QualifiedName)simpleName.getParent();
											}
											else if(simpleName.getParent() instanceof FieldAccess) {
												parentExpression = (FieldAccess)simpleName.getParent();
											}
											else {
												parentExpression = simpleName;
											}
											boolean isLeftHandOfAssignment = false;
											if(parentExpression.getParent() instanceof Assignment) {
												Assignment assignment = (Assignment)parentExpression.getParent();
												Expression leftHandSide = assignment.getLeftHandSide();
												SimpleName leftHandSideName = null;
												if(leftHandSide instanceof SimpleName) {
													leftHandSideName = (SimpleName)leftHandSide;
												}
												else if(leftHandSide instanceof QualifiedName) {
													QualifiedName leftHandSideQualifiedName = (QualifiedName)leftHandSide;
													leftHandSideName = leftHandSideQualifiedName.getName();
												}
												else if(leftHandSide instanceof FieldAccess) {
													FieldAccess leftHandSideFieldAccess = (FieldAccess)leftHandSide;
													leftHandSideName = leftHandSideFieldAccess.getName();
												}
												if(leftHandSideName != null && leftHandSideName.equals(simpleName)) {
													isLeftHandOfAssignment = true;
													typeCheckElimination.addAssignedField(fragment);
												}
											}
											if(!isLeftHandOfAssignment)
												typeCheckElimination.addAccessedField(fragment);
										}
									}
								}
							}
						}
						else if(variableInstructionVariableBinding.isParameter()) {
							List<SingleVariableDeclaration> parameters = typeCheckMethod.parameters();
							for(SingleVariableDeclaration parameter : parameters) {
								IVariableBinding parameterVariableBinding = parameter.resolveBinding();
								if(parameterVariableBinding.isEqualTo(variableInstructionVariableBinding)) {
									boolean isLeftHandOfAssignment = false;
									if(simpleName.getParent() instanceof Assignment) {
										Assignment assignment = (Assignment)simpleName.getParent();
										Expression leftHandSide = assignment.getLeftHandSide();
										if(leftHandSide instanceof SimpleName) {
											SimpleName leftHandSideName = (SimpleName)leftHandSide;
											if(leftHandSideName.equals(simpleName)) {
												isLeftHandOfAssignment = true;
												typeCheckElimination.addAssignedParameter(parameter);
											}
										}
									}
									if(!isLeftHandOfAssignment)
										typeCheckElimination.addAccessedParameter(parameter);
									break;
								}
							}
						}
						//checking for local variables accessed inside the type-checking code branches, but declared outside them
						else {
							for(Statement vDStatement : variableDeclarationStatementsInsideTypeCheckMethodApartFromTypeCheckCodeFragment) {
								VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement)vDStatement;
								List<VariableDeclarationFragment> fragments = variableDeclarationStatement.fragments();
								for(VariableDeclarationFragment fragment : fragments) {
									IVariableBinding fragmentVariableBinding = fragment.resolveBinding();
									if(fragmentVariableBinding.isEqualTo(variableInstructionVariableBinding)) {
										boolean isLeftHandOfAssignment = false;
										if(simpleName.getParent() instanceof Assignment) {
											Assignment assignment = (Assignment)simpleName.getParent();
											Expression leftHandSide = assignment.getLeftHandSide();
											if(leftHandSide instanceof SimpleName) {
												SimpleName leftHandSideName = (SimpleName)leftHandSide;
												if(leftHandSideName.equals(simpleName)) {
													isLeftHandOfAssignment = true;
													typeCheckElimination.addAssignedLocalVariable(fragment);
												}
											}
										}
										if(!isLeftHandOfAssignment)
											typeCheckElimination.addAccessedLocalVariable(fragment);
										break;
									}
								}    											
							}
						}
					}
				}
			}
		}
		processRemainingIfStatementExpressions(variableDeclarationStatementsInsideTypeCheckMethodApartFromTypeCheckCodeFragment);
	}

	private void processRemainingIfStatementExpressions(List<Statement> variableDeclarationStatementsInsideTypeCheckMethodApartFromTypeCheckCodeFragment) {
		ExpressionExtractor expressionExtractor = new ExpressionExtractor();
		for(Expression complexExpression : complexExpressionMap.keySet()) {
			DefaultMutableTreeNode root = typeCheckElimination.getRemainingIfStatementExpression(complexExpression);
			if(root != null) {
				DefaultMutableTreeNode leaf = root.getFirstLeaf();
				while(leaf != null) {
					Expression leafExpression = (Expression)leaf.getUserObject();
					//checking for methods of the Source class invoked inside the type-checking branches
					List<Expression> methodInvocations = expressionExtractor.getMethodInvocations(leafExpression);
					for(Expression expression : methodInvocations) {
						if(expression instanceof MethodInvocation) {
							MethodInvocation methodInvocation = (MethodInvocation)expression;
							IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
							if(methodBinding.getDeclaringClass().isEqualTo(typeDeclaration.resolveBinding())) {
								for(MethodDeclaration method : methods) {
									if(method.resolveBinding().isEqualTo(methodBinding)) {
										typeCheckElimination.addAccessedMethod(method);
									}
								}
							}
							else if(methodInvocation.getExpression() == null || (methodInvocation.getExpression() != null && methodInvocation.getExpression() instanceof ThisExpression)) {
								ITypeBinding superclassTypeBinding = typeDeclaration.resolveBinding().getSuperclass();
								while(superclassTypeBinding != null && !superclassTypeBinding.isEqualTo(methodBinding.getDeclaringClass())) {
									superclassTypeBinding = superclassTypeBinding.getSuperclass();
								}
								if(methodBinding.getDeclaringClass().isEqualTo(superclassTypeBinding))
									typeCheckElimination.addSuperAccessedMethod(methodBinding);
							}
						}
					}
					//checking for Source class fields or parameters of the type-checking method accessed inside the type-checking branches
					List<Expression> variableInstructions = expressionExtractor.getVariableInstructions(leafExpression);
					for(Expression variableInstruction : variableInstructions) {
						SimpleName simpleName = (SimpleName)variableInstruction;
						IBinding variableInstructionBinding = simpleName.resolveBinding();
						if(variableInstructionBinding.getKind() == IBinding.VARIABLE) {
							IVariableBinding variableInstructionVariableBinding = (IVariableBinding)variableInstructionBinding;
							if(variableInstructionVariableBinding.isField()) {
								if(variableInstructionVariableBinding.getDeclaringClass() != null && variableInstructionVariableBinding.getDeclaringClass().isEqualTo(typeDeclaration.resolveBinding())) {
									for(FieldDeclaration field : fields) {
										List<VariableDeclarationFragment> fragments = field.fragments();
										for(VariableDeclarationFragment fragment : fragments) {
											IVariableBinding fragmentVariableBinding = fragment.resolveBinding();
											if(fragmentVariableBinding.isEqualTo(variableInstructionVariableBinding)) {
												Expression parentExpression = null;
												if(simpleName.getParent() instanceof QualifiedName) {
													parentExpression = (QualifiedName)simpleName.getParent();
												}
												else if(simpleName.getParent() instanceof FieldAccess) {
													parentExpression = (FieldAccess)simpleName.getParent();
												}
												else {
													parentExpression = simpleName;
												}
												boolean isLeftHandOfAssignment = false;
												if(parentExpression.getParent() instanceof Assignment) {
													Assignment assignment = (Assignment)parentExpression.getParent();
													Expression leftHandSide = assignment.getLeftHandSide();
													SimpleName leftHandSideName = null;
													if(leftHandSide instanceof SimpleName) {
														leftHandSideName = (SimpleName)leftHandSide;
													}
													else if(leftHandSide instanceof QualifiedName) {
														QualifiedName leftHandSideQualifiedName = (QualifiedName)leftHandSide;
														leftHandSideName = leftHandSideQualifiedName.getName();
													}
													else if(leftHandSide instanceof FieldAccess) {
														FieldAccess leftHandSideFieldAccess = (FieldAccess)leftHandSide;
														leftHandSideName = leftHandSideFieldAccess.getName();
													}
													if(leftHandSideName != null && leftHandSideName.equals(simpleName)) {
														isLeftHandOfAssignment = true;
														typeCheckElimination.addAssignedField(fragment);
													}
												}
												if(!isLeftHandOfAssignment)
													typeCheckElimination.addAccessedField(fragment);
											}
										}
									}
								}
							}
							else if(variableInstructionVariableBinding.isParameter()) {
								List<SingleVariableDeclaration> parameters = typeCheckMethod.parameters();
								for(SingleVariableDeclaration parameter : parameters) {
									IVariableBinding parameterVariableBinding = parameter.resolveBinding();
									if(parameterVariableBinding.isEqualTo(variableInstructionVariableBinding)) {
										boolean isLeftHandOfAssignment = false;
										if(simpleName.getParent() instanceof Assignment) {
											Assignment assignment = (Assignment)simpleName.getParent();
											Expression leftHandSide = assignment.getLeftHandSide();
											if(leftHandSide instanceof SimpleName) {
												SimpleName leftHandSideName = (SimpleName)leftHandSide;
												if(leftHandSideName.equals(simpleName)) {
													isLeftHandOfAssignment = true;
													typeCheckElimination.addAssignedParameter(parameter);
												}
											}
										}
										if(!isLeftHandOfAssignment)
											typeCheckElimination.addAccessedParameter(parameter);
										break;
									}
								}
							}
							//checking for local variables accessed inside the type-checking code branches, but declared outside them
							else {
								for(Statement vDStatement : variableDeclarationStatementsInsideTypeCheckMethodApartFromTypeCheckCodeFragment) {
									VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement)vDStatement;
									List<VariableDeclarationFragment> fragments = variableDeclarationStatement.fragments();
									for(VariableDeclarationFragment fragment : fragments) {
										IVariableBinding fragmentVariableBinding = fragment.resolveBinding();
										if(fragmentVariableBinding.isEqualTo(variableInstructionVariableBinding)) {
											boolean isLeftHandOfAssignment = false;
											if(simpleName.getParent() instanceof Assignment) {
												Assignment assignment = (Assignment)simpleName.getParent();
												Expression leftHandSide = assignment.getLeftHandSide();
												if(leftHandSide instanceof SimpleName) {
													SimpleName leftHandSideName = (SimpleName)leftHandSide;
													if(leftHandSideName.equals(simpleName)) {
														isLeftHandOfAssignment = true;
														typeCheckElimination.addAssignedLocalVariable(fragment);
													}
												}
											}
											if(!isLeftHandOfAssignment)
												typeCheckElimination.addAccessedLocalVariable(fragment);
											break;
										}
									}    											
								}
							}
						}
					}
					leaf = leaf.getNextLeaf();
				}
			}
		}
	}

	private Expression extractOperand(Expression operand) {
		if(operand instanceof SimpleName) {
			SimpleName operandSimpleName = (SimpleName)operand;
			return operandSimpleName;
		}
		else if(operand instanceof QualifiedName) {
			QualifiedName operandQualifiedName = (QualifiedName)operand;
			return operandQualifiedName.getName();
		}
		else if(operand instanceof FieldAccess) {
			FieldAccess operandFieldAccess = (FieldAccess)operand;
			return operandFieldAccess.getName();
		}
		else if(operand instanceof MethodInvocation) {
			MethodInvocation methodInvocation = (MethodInvocation)operand;
			for(MethodDeclaration method : methods) {
				SimpleName fieldInstruction = MethodDeclarationUtility.isGetter(method);
				if(fieldInstruction != null && method.resolveBinding().isEqualTo(methodInvocation.resolveMethodBinding())) {
					return fieldInstruction;
				}
				MethodInvocation delegateMethodInvocation = MethodDeclarationUtility.isDelegate(method);
				if(delegateMethodInvocation != null && method.resolveBinding().isEqualTo(methodInvocation.resolveMethodBinding())) {
					return delegateMethodInvocation;
				}
			}
			return methodInvocation;
		}
		return null;
	}

	private SimpleName containsTypeVariableKey(SimpleName simpleName) {
		for(SimpleName keySimpleName : typeVariableCounterMap.keySet()) {
			if(keySimpleName.resolveBinding().isEqualTo(simpleName.resolveBinding()))
				return keySimpleName;
		}
		return null;
	}

	private MethodInvocation containsTypeMethodInvocationKey(MethodInvocation methodInvocation) {
		for(MethodInvocation keyMethodInvocation : typeMethodInvocationCounterMap.keySet()) {
			if(keyMethodInvocation.resolveMethodBinding().isEqualTo(methodInvocation.resolveMethodBinding()))
				return keyMethodInvocation;
		}
		return null;
	}
}
