package org.hyperic.hq.scheduler;
// Generated Nov 3, 2006 4:40:15 PM by Hibernate Tools 3.1.0.beta4



/**
 * QzLock generated by hbm2java
 */
public class QzLock  implements java.io.Serializable {

    // Fields    

     private String lockName;

     // Constructors

    /** default constructor */
    public QzLock() {
    }

    /** full constructor */
    public QzLock(String lockName) {
        this.lockName = lockName;
    }
    
   
    // Property accessors
    public String getLockName() {
        return this.lockName;
    }
    
    public void setLockName(String lockName) {
        this.lockName = lockName;
    }


   public boolean equals(Object other) {
         if ( (this == other ) ) return true;
		 if ( (other == null ) ) return false;
		 if ( !(other instanceof QzLock) ) return false;
		 QzLock castOther = ( QzLock ) other; 
         
		 return ( (this.getLockName()==castOther.getLockName()) || ( this.getLockName()!=null && castOther.getLockName()!=null && this.getLockName().equals(castOther.getLockName()) ) );
   }
   
   public int hashCode() {
         int result = 17;
         
         result = 37 * result + ( getLockName() == null ? 0 : this.getLockName().hashCode() );
         return result;
   }   


}


