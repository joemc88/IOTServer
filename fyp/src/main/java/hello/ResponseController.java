package hello;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.mongodb.*;	
//import java.util.ArrayList;
//import org.json.simple.JSONArray;
//import org.json.simple.JSONObject;
//import org.json.simple.parser.JSONParser;
//import org.json.simple.parser.ParseException;

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
    		DBObject currentContext =  contexts.findOne( new BasicDBObject().append("CID", users.findOne(new BasicDBObject().append("UID",uid)).get("currentContext")));

    		
    		//Get adjacency List and weights of current context
    		Object[] currentAdjacencyObj = ((BasicDBList)currentContext.get("adjacencyList")).toArray();
    		Object[] currentWeightsObj = ((BasicDBList) currentContext.get("weights")).toArray();
  	      	int[] currentAdjacency = new int[currentAdjacencyObj.length];
  	        double[] currentWeights = new double[currentWeightsObj.length];

  	        for(int i=0; i < currentWeightsObj.length; i++){
  	        	currentAdjacency[i] = (int) currentAdjacencyObj[i];
  	        	currentWeights[i] = Double.valueOf(currentWeightsObj[i].toString());
  	        }
    		
  	          
  	        //Second we check if the new data corresponds to an existing record
    		DBObject CIDofNewCurrent = contexts.findOne(new BasicDBObject("services", services)
    				.append("day", day)
    				.append("hour",hour), new BasicDBObject("CID", 1));
    		
    		//if not we create one with CID (context id) set to one above the last greatest id
    		if(CIDofNewCurrent == null){
    			greatestCID++;
    			BasicDBObject newContext = new BasicDBObject("CID", greatestCID)
    	        		.append("services",services)
    	        		.append("day", day)
        				.append("hour",hour)
        				.append("adjacencyList", new int[]{1,0,0,0,0})
     	     	        .append("weights",new double[]{1.0,0.0,0.0,0.0,0.0});   
    	        contexts.insert(newContext);
    	
    	        //find the index in current adjacency list to replace. It will be the one with the lowest weight, i.e the least used one.
    	        int replaceIndex = 0;
    	        double lowestWeight  =1;
    	        for(int i = 0; i< currentAdjacency.length; i++){
    	        	if(currentWeights[i]< lowestWeight){
    	        		lowestWeight = currentWeights[i];
    	        		replaceIndex = i;
    	        	}
    	        }
    	        currentAdjacency[replaceIndex] = greatestCID;
    	        
    	        //create query object to replace currents adjacency list and weights with updated version
    	        double[] redistribution = redistributeWeights(greatestCID, currentAdjacency, currentWeights);
    	        updateCurrentsFuture.append("$set", new BasicDBObject().append("weights", redistribution).append("adjacencyList", currentAdjacency));
    	        latestCID = greatestCID;
    	        
    		}else{
    			//if received context does exist, we'll use it's CID
    			latestCID =  (int) CIDofNewCurrent.get("CID");			
    			
    			//update weights here
    			 double[] redistribution = redistributeWeights(latestCID, currentAdjacency, currentWeights);
     	        updateCurrentsFuture.append("$set", new BasicDBObject().append("weights", redistribution).append("adjacencyList", currentAdjacency));
    	
    		}   			
    		
    		//Thirdly we get the context the user points to and point IT at the newly found/created context
    		BasicDBObject contextPointUpdateQuery = new BasicDBObject()
    				.append("CID", users.findOne(new BasicDBObject().append("UID",uid)).get("currentContext"));

    		contexts.update(contextPointUpdateQuery, updateCurrentsFuture);
    		
    		//Fourth we change the value of the users current context to point to the latest data 
    		BasicDBObject updateUsersCurrent = new BasicDBObject().append("$set",new BasicDBObject().append("currentContext",latestCID));
    		
    		BasicDBObject userContextUpdateQuery = new BasicDBObject().append("UID", uid);
    		users.update(userContextUpdateQuery, updateUsersCurrent );
    		
    		//finally return the services of the new currents future
    		//this must return the CID that corresponds to the largest weight on the current contexts adjacency list
    		int usersCurrentContextID  = (int) users.findOne(new BasicDBObject().append("UID",uid)).get("currentContext");
    		DBObject nextContextAfterCurrent =  contexts.findOne(new BasicDBObject("CID", usersCurrentContextID), new BasicDBObject("adjacencyList", 1).append("weights",1));
    		Object[] possibleFutureContexts =((BasicDBList) nextContextAfterCurrent.get("weights")).toArray();
    		
    		//find index of adjacency list with greatest corresponding weight
    		int winnerIndex = 0;
    		double largestWeight = 0;
    		for(int i = 0; i< possibleFutureContexts.length;i++){
    			if(( Double.valueOf(possibleFutureContexts[i].toString()))> largestWeight){
    				winnerIndex = i;
    				largestWeight = Double.valueOf(possibleFutureContexts[i].toString());
    			}
    		}
    		Object[] adj = ((BasicDBList) nextContextAfterCurrent.get("adjacencyList")).toArray();
    		int predictionCID  =(int) adj[winnerIndex];
    		
    		DBObject arrayResults = contexts.findOne(new BasicDBObject("CID",predictionCID), new BasicDBObject("services", 1));
    		//TODO if context has no services continue through linked list until one with services is found

    		BasicDBList serv = (BasicDBList) arrayResults.get("services");
    		Object[] servArr = serv.toArray();
    		int i = 0;
    		for(Object dbObj : servArr) {
    			futureServices[i] = dbObj.toString();
    			System.out.println(dbObj.toString());
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
	        System.out.println("Connected to database successfully");
				
	        DBCollection contexts = db.createCollection("contexts", null);
	        
	        DBCollection users = db.createCollection("users", null);
	        //TODO create a new feature. add a cell to user object to keep track of greatestCID
	        //add cell to user to track recurring sets of services detected across multiple contexts.
	        //create new collection for macros. 
	        //if a newly created context has the same services as the previous one it will point to the same macro, contexts must keep a list of macros to allow user defined ones
	        //each macro is a list of tuples. <service, time>
	        
	        //add endpoint to handle when a service is used. it must add it to 
	        BasicDBObject doc = new BasicDBObject("UID", 1).
	        		append("Email", "joesph.joemc.mcevoy@gmail.com").
	     	        append("currentContext", 1);
	     	     				
	        users.insert(doc);
	        System.out.println("Document inserted successfully");
	        String[] services = {"Undefined","Undefined","Undefined","Undefined","Undefined"};
	        int[] adjacencyList = {1,0,0,0,0};
	       double[] weights= {1.0,0.0,0.0,0.0,0.0};
	        BasicDBObject baseContext = new BasicDBObject("CID", 1).
	        		append("day",0).
	        		append("hour",0).
	        		append("services",services).
	     	        append("adjacencyList", adjacencyList).
	     	        append("weights",weights);
	        
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
    //method to redistribute the weights 
    private double[] redistributeWeights(int usedCID, int[] adjacencyList, double[] weights){
    	double alpha = weights.length;
    	for(int i=0; i< adjacencyList.length;i++){
    		if(adjacencyList[i] != usedCID&& adjacencyList[i]>(1.0/alpha)/(alpha-1.0)){
    			weights[i] -= (1.0/alpha)/(alpha-1.0);
    			System.out.println("weight lowered by: "+((1.0/alpha)/(alpha-1.0)));
    		}else{
    			if(weights[i]<=1-(1.0/alpha)){
    				weights[i] += 1.0/alpha;
    				System.out.println("weight increased by:"+(1.0/alpha));
    			}
    		}
    	}
    	return weights;
    }
}