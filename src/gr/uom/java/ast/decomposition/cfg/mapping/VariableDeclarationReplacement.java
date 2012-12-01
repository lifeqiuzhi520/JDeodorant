package gr.uom.java.ast.decomposition.cfg.mapping;

import gr.uom.java.ast.LocalVariableDeclarationObject;

public class VariableDeclarationReplacement implements Replacement {
	private LocalVariableDeclarationObject v1;
	private LocalVariableDeclarationObject v2;
	
	public VariableDeclarationReplacement(LocalVariableDeclarationObject v1, LocalVariableDeclarationObject v2) {
		this.v1 = v1;
		this.v2 = v2;
	}

	public String getValue1() {
		return v1.getName();
	}

	public String getValue2() {
		return v2.getName();
	}

	public String toString() {
		return v1 + " -> " + v2;
	}
}
