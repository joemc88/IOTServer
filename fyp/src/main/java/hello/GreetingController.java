package hello;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.mongodb.*;	
//import java.util.ArrayList;
//import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

@RestController
public class GreetingController {
	//TODO initialise these properly to they recieve their values from the DB, otherwise the server will start from scratch each reboot
	private int greatestCID = 1;
	private int greatestUID = 1;
	private int latestCID =1;
    @RequestMapping("/checkIn")
    public Response reply(
    	@RequestParam(value="uid", defaultValue="0000") int uid ,//who are you?
    	@RequestParam(value="day", defaultValue="0") int day,
    	@RequestParam(value="hour", defaultValue="0") int hour,
    	@RequestParam(value="services", defaultValue="none") String[] services){ //what can you see
    	
    	String futureServices[] = {"Uncharted","territory","cannot","make","predictions"};
    	System.out.println("beginning try block");
    	//Put in DB stuff here
    	try{
    		MongoClient mongoClient = new MongoClient("localhost", 27017);
    		DB db = mongoClient.getDB( "test" );
    		DBCollection users = db.getCollection("users");
    		DBCollection contexts = db.getCollection("contexts");

    	
    		/* New data has come in. We need to tell the last context where it will point to
    		 * from now on. 
    		 * 
    		 * First we create an object to hold the update
    		 * */
    		
    		BasicDBObject updateCurrentsFuture = new BasicDBObject();
    		
    		//Second we check if the new data corresponds to an existing record
    		System.out.println("finding entry for recieved context");
    		DBObject CIDofNewCurrent = contexts.findOne(new BasicDBObject("services", services)
    				//.append("day", day)
    				.append("hour",hour), new BasicDBObject("CID", 1));
    		System.out.println("search completed");
    		
    		//if not we create one with CID (context id) set to one above the last greatest id
    		if(CIDofNewCurrent == null){
    			System.out.println("no entry found creating a new one");
    			greatestCID++;
    			BasicDBObject newContext = new BasicDBObject("CID", greatestCID)
    	        		.append("services",services)
    	        		.append("day", day)
        				.append("hour",hour)
    	     	       . append("nextContext", 1);   
    	        contexts.insert(newContext);

    	        updateCurrentsFuture.append("$set", new BasicDBObject().append("nextContext", greatestCID));
    	        latestCID = greatestCID;
    	        
    		}else{
    			System.out.println("entry found point last context to it");
    			System.out.println(CIDofNewCurrent.get("CID"));
    			latestCID =  (int) CIDofNewCurrent.get("CID");
    			updateCurrentsFuture.append("$set", new BasicDBObject().append("nextContext", latestCID));
    			
    			System.out.println("cid of new current used without problems");
    		}   			
    		//Thirdly we get the record corresponding to the last context and update where it points to the new one 
    		System.out.println("updating where last context pointed");
    		BasicDBObject contextPointUpdateQuery = new BasicDBObject()
    				.append("CID", users.findOne().get("currentContext"));

    		contexts.update(contextPointUpdateQuery, updateCurrentsFuture);
    		
    		//Fourth we change the value of the users current context to point to the latest data 
    		System.out.println("updating users context");
    		System.out.print("with value ");
    		//System.out.println(CIDofNewCurrent.get("CID"));
    		System.out.print("thats the value above");
    		BasicDBObject updateUsersCurrent = new BasicDBObject().append("$set",new BasicDBObject().append("currentContext",latestCID));
    		
    		BasicDBObject userContextUpdateQuery = new BasicDBObject().append("UID", uid);
    		users.update(userContextUpdateQuery, updateUsersCurrent );
    		
    		//finally return the services of the new currents future
    		//System.out.println(" after status:"+users.findOne());
    		System.out.println("finding out the future");
    		int usersCurrentContextID  = (int) users.findOne().get("currentContext");
    		DBObject nextContextAfterCurrent =  contexts.findOne(new BasicDBObject("CID", usersCurrentContextID), new BasicDBObject("nextContext", 1));

    		DBObject arrayResults = contexts.findOne(new BasicDBObject("CID", nextContextAfterCurrent.get("nextContext")), new BasicDBObject("services", 1));
    		//TODO if context has no services continue through linked list until one with services is found
    		System.out.println("getting array from the future");
    		BasicDBList serv = (BasicDBList) arrayResults.get("services");
   
    		Object[] servArr = serv.toArray();
    		int i = 0;
    		for(Object dbObj : servArr) {
    			futureServices[i] = dbObj.toString();
    			i++;
    		  }
    		mongoClient.close();
    	}catch(Exception e){
    		System.out.println(e);
    	}
    	return new Response(uid,futureServices);
    }
    
    //simple method to check connection
    @RequestMapping("/share")
    public String[] confirm(){
    	String a[] = {"value1","value2"};
    	return a;
    }   
    //method to reset db after changes for testing
    @RequestMapping("/initialise")
    public String initiaise(){
    	String retVal ="";
    	try{
	    	MongoClient mongoClient = new MongoClient("localhost", 27017);
	        DB db = mongoClient.getDB( "test" );
	        db.dropDatabase();
	        db = mongoClient.getDB( "test" );
	        System.out.println("Connect to database successfully");
				
	        DBCollection contexts = db.createCollection("contexts", null);
	        System.out.println("context Collection created successfully");
	        
	        DBCollection users = db.createCollection("users", null);
	        System.out.println("user Collection created successfully");
	        
	        BasicDBObject doc = new BasicDBObject("UID", 1).
	        		append("Email", "joesph.joemc.mcevoy@gmail.com").
	     	        append("currentContext", 1);
	     	     				
	        users.insert(doc);
	        System.out.println("Document inserted successfully");
	        String[] services = {"Undefined","Undefined","Undefined","Undefined","Undefined"};
	
	        BasicDBObject baseContext = new BasicDBObject("CID", 1).
	        		append("day",0).
	        		append("hour",0).
	        		append("services",services).
	     	        append("nextContext", 1);
	        
	        contexts.insert(baseContext);
	        DBCursor cursor = contexts.find();
	        int i = 1;
	
	        while (cursor.hasNext()) { 
	            System.out.println("Inserted Document: "+i); 
	            System.out.println(cursor.next()); 
	            retVal = "everything worked fine";
	        } 
    	}catch(Exception e){
    		retVal = "initialisation failed "+e;
    	}
    	return retVal;
    } 
}