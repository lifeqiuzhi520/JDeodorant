package ca.concordia.jdeodorant.clone.parsers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class CloneGroup {
	
	private int cloneGroupID;
	private Set<CloneInstance> cloneInstances = new LinkedHashSet<CloneInstance>();
	private CloneGroup repeatedOf;
	private CloneGroup subCloneOf;
	
	public CloneGroup(int groupID) {
		setCloneGroupID(groupID);
	}

	public int getCloneGroupID() {
		return cloneGroupID;
	}

	public void setCloneGroupID(int cloneGroupID) {
		this.cloneGroupID = cloneGroupID;
	}

	public void addClone(CloneInstance cloneInstance) {
		cloneInstances.add(cloneInstance);
		cloneInstance.setBelongingCloneGroup(this);
	}
	
	public List<CloneInstance> getCloneInstances() {
		List<CloneInstance> copyToReturn = new ArrayList<CloneInstance>();
		for (CloneInstance cloneInstance : cloneInstances)
			copyToReturn.add(cloneInstance);
		return copyToReturn;
	}
	
	@Override
	public boolean equals(Object obj) {

		if (obj == null)
			return false;
		
		if (obj.getClass() != CloneGroup.class)
			return false;
		
		CloneGroup otherCloneGroup = (CloneGroup)obj;
		
		if (getCloneGroupSize() != otherCloneGroup.getCloneGroupSize())
			return false;
		
		return this.cloneInstances.equals(otherCloneGroup.cloneInstances);
		
	}
	
	@Override
	public int hashCode() {
		return cloneInstances.hashCode();
	}
	
	
	public int getCloneGroupSize() {
		return cloneInstances.size();
	}
	
	@Override
	public String toString() {
		return String.format("Clone group ID: %s%sNumber of clone instances: %s", cloneGroupID, System.lineSeparator(), getCloneGroupSize());
	}

	public CloneGroup getRepeatedOf() {
		return repeatedOf;
	}

	public void setRepeatedOf(CloneGroup repeatedOf) {
		this.repeatedOf = repeatedOf;
	}
	
	public boolean isRepeated() {
		return repeatedOf != null;
	}

	public boolean containsClassLevelClone() {
		for (CloneInstance cloneInstance : cloneInstances)
			if (cloneInstance.isClassLevelClone())
				return true;
		return false;
	}

	public CloneGroup getSubcloneOf() {
		return this.subCloneOf;
	}
	
	public void setSubCloneOf(CloneGroup subCloneOf) {
		this.subCloneOf = subCloneOf;
	}

	public boolean isSubClone() {
		return this.subCloneOf != null;
	}

	public boolean isSubCloneOf(CloneGroup otherCloneGroup) {
		for (CloneInstance cloneInstance : this.cloneInstances) {
			boolean isSubClone = false;
			for (CloneInstance otherCloneInstance : otherCloneGroup.cloneInstances) {
				if (cloneInstance.isSubcloneOf(otherCloneInstance)) {
					isSubClone = true;
					break;
				}
			}
			if (!isSubClone)
				return false;
		}
		return true;
	}
	
}