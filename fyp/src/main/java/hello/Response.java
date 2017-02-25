package hello;

public class Response {
	private final int ID;
	private String []items;

		   
	public Response(int ID, String[] items){
		this.ID = ID;
		this.items = items;//any thing we can predict you'll see in the near future based on the past
    }


	public int getID(){
	   	return ID;
	}
	public String[] getItem() {
		return items;
	}
	  
}