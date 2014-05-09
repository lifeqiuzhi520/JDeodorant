package gr.uom.java.ast.decomposition.cfg.mapping.precondition;

import gr.uom.java.ast.decomposition.AbstractStatement;

import org.eclipse.jface.viewers.StyledString;

public class StatementPreconditionViolation extends PreconditionViolation {
	private AbstractStatement statement;
	
	public StatementPreconditionViolation(AbstractStatement statement, PreconditionViolationType type) {
		super(type);
		this.statement = statement;
	}

	public AbstractStatement getStatement() {
		return statement;
	}

	public String getViolation() {
		StringBuilder sb = new StringBuilder();
		if(type.equals(PreconditionViolationType.UNMATCHED_STATEMENT_CANNOT_BE_MOVED_BEFORE_OR_AFTER_THE_EXTRACTED_CODE)) {
			sb.append("Unmatched statement ");
			String str = statement.toString();
			sb.append(str.substring(0, str.lastIndexOf("\n")));
			sb.append(" ");
			sb.append(type.toString());
		}
		else if(type.equals(PreconditionViolationType.UNMATCHED_BREAK_STATEMENT) ||
				type.equals(PreconditionViolationType.UNMATCHED_CONTINUE_STATEMENT) ||
				type.equals(PreconditionViolationType.UNMATCHED_RETURN_STATEMENT)) {
			sb.append("Unmatched ");
			String str = statement.toString();
			sb.append(str.substring(0, str.lastIndexOf("\n")));
		}
		else if(type.equals(PreconditionViolationType.BREAK_STATEMENT_WITHOUT_LOOP) ||
				type.equals(PreconditionViolationType.CONTINUE_STATEMENT_WITHOUT_LOOP)) {
			sb.append("Statement ");
			String str = statement.toString();
			sb.append(str.substring(0, str.lastIndexOf("\n")));
			sb.append(" without innermost loop");
		}
		else if(type.equals(PreconditionViolationType.CONDITIONAL_RETURN_STATEMENT)) {
			sb.append("Conditional ");
			String str = statement.toString();
			sb.append(str.substring(0, str.lastIndexOf("\n")));
		}
		return sb.toString();
	}

	@Override
	public StyledString getStyledViolation() {
		StyledString styledString = new StyledString();
		BoldStyler styler = new BoldStyler();
		if(type.equals(PreconditionViolationType.UNMATCHED_STATEMENT_CANNOT_BE_MOVED_BEFORE_OR_AFTER_THE_EXTRACTED_CODE)) {
			styledString.append("Unmatched statement ");
			String str = statement.toString();
			styledString.append(str.substring(0, str.lastIndexOf("\n")), styler);
			styledString.append(" ");
			styledString.append(type.toString());
		}
		else if(type.equals(PreconditionViolationType.UNMATCHED_BREAK_STATEMENT) ||
				type.equals(PreconditionViolationType.UNMATCHED_CONTINUE_STATEMENT) ||
				type.equals(PreconditionViolationType.UNMATCHED_RETURN_STATEMENT)) {
			styledString.append("Unmatched ");
			String str = statement.toString();
			styledString.append(str.substring(0, str.lastIndexOf("\n")), styler);
		}
		else if(type.equals(PreconditionViolationType.BREAK_STATEMENT_WITHOUT_LOOP) ||
				type.equals(PreconditionViolationType.CONTINUE_STATEMENT_WITHOUT_LOOP)) {
			styledString.append("Statement ");
			String str = statement.toString();
			styledString.append(str.substring(0, str.lastIndexOf("\n")), styler);
			styledString.append(" without innermost loop");
		}
		else if(type.equals(PreconditionViolationType.CONDITIONAL_RETURN_STATEMENT)) {
			styledString.append("Conditional ");
			String str = statement.toString();
			styledString.append(str.substring(0, str.lastIndexOf("\n")), styler);
		}
		return styledString;
	}
}
