package gr.uom.java.ast.decomposition.cfg;

public class PDGDataDependence extends PDGDependence {
	private AbstractVariable data;
	private boolean loopCarried;
	private volatile int hashCode = 0;
	
	public PDGDataDependence(PDGNode src, PDGNode dst, AbstractVariable data, boolean loopCarried) {
		super(src, dst);
		this.data = data;
		this.loopCarried = loopCarried;
		src.addOutgoingEdge(this);
		dst.addIncomingEdge(this);
	}

	public AbstractVariable getData() {
		return data;
	}

	public boolean isLoopCarried() {
		return loopCarried;
	}

	public boolean equals(Object o) {
		if(this == o)
    		return true;
		
		if(o instanceof PDGDataDependence) {
			PDGDataDependence dataDependence = (PDGDataDependence)o;
			return this.src.equals(dataDependence.src) && this.dst.equals(dataDependence.dst) &&
				this.data.equals(dataDependence.data);
		}
		return false;
	}

	public int hashCode() {
		if(hashCode == 0) {
			int result = 17;
			result = 37*result + src.hashCode();
			result = 37*result + dst.hashCode();
			result = 37*result + data.hashCode();
			hashCode = result;
		}
		return hashCode;
	}

	public String toString() {
		return src.toString() + "-->" + data.toString() + "\n" + dst.toString();
	}
}