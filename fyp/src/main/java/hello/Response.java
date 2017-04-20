package hello;

public class Response {
	private final int ID;
	private String []items;		   
	public Response(int ID, String[] items){
		this.ID = ID;
		this.items = items;
    }
	public int getID(){
	   	return ID;
	}
	public String[] getItem() {
		return items;
	}
	  
}