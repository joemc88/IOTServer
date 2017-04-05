package hello;

import java.util.HashMap;
import java.util.Map;
import java.io.*;
import java.util.*;
import gnu.io.*;
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
public class ResponseController {
	//TODO initialise these properly to they recieve their values from the DB, otherwise the server will start from scratch each reboot
	private int greatestCID = 1;
	//private int greatestUID = 1;
	private int latestCID =1;
	private int lastCheckin;
	String lastServices[] = {"","",""};
	String retArray[];
	SerialPort serialPort =  null;
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
    	//	DBCollection macros = db.getCollection("macros");
    	
    		/* New data has come in. We need to tell the last context where it will point to
    		 * from now on. 
    		 * 
    		 * First we create an object to hold the update
    		 * */
    		if(lastCheckin!= hour){
    			lastCheckin = hour;
    		
    		BasicDBObject updateCurrentsFuture = new BasicDBObject();
    		DBObject currentContext =  contexts.findOne( new BasicDBObject().append("CID", users.findOne(new BasicDBObject().append("UID",uid)).get("currentContext")));
    	
    		//TOOD put in an if statement to ensure updating current doesn't break by pointing to itself
    		//Get adjacency List and weights of current context
    		Object[] currentAdjacencyObj = ((BasicDBList)currentContext.get("adjacencyList")).toArray();
    		Object[] currentWeightsObj = ((BasicDBList) currentContext.get("weights")).toArray();
  	      	int[] currentAdjacency = new int[currentAdjacencyObj.length];
  	        double[] currentWeights = new double[currentWeightsObj.length];

  	        System.out.println("getting currents adjaceny and weights");
  	        for(int i=0; i < currentWeightsObj.length; i++){
  	        	
  	        	currentAdjacency[i] = (int) currentAdjacencyObj[i];
  	        	 System.out.println("adj"+currentAdjacency[i]);
  	        	currentWeights[i] = Double.valueOf(currentWeightsObj[i].toString());
  	        	System.out.println("weight");
  	        	 System.out.println(currentWeights[i]);
  	        }
    		
  	          
  	        //Second we check if the new data corresponds to an existing record
    		DBObject CIDofNewCurrent = contexts.findOne(new BasicDBObject("services", services)
    				.append("day", day)
    				.append("hour",hour), new BasicDBObject("CID", 1).append("hour", hour));
    		
    		//if not we create one with CID (context id) set to one above the last greatest id
    		
    		
    		
	    		if(CIDofNewCurrent == null){
	    			greatestCID++;
	    			BasicDBObject newContext = new BasicDBObject("CID", greatestCID)
	    	        		.append("services",services)
	    	        		.append("day", day)
	        				.append("hour",hour)
	        				.append("adjacencyList", new int[]{1,0,0,0,0})
	     	     	        .append("weights",new double[]{0.001,0.0,0.0,0.0,0.0});   
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
	    	        System.out.println("NEW:updated weights with to increase"+greatestCID);
	    	        //create query object to replace currents adjacency list and weights with updated version
	    	        double[] redistribution = redistributeWeights(greatestCID, currentAdjacency, currentWeights);
	    	        updateCurrentsFuture.append("$set", new BasicDBObject().append("weights", redistribution).append("adjacencyList", currentAdjacency));
	    	        latestCID = greatestCID;
	    	        
	    		}else{
	    			//if received context does exist, we'll use it's CID
	    			latestCID =  (int) CIDofNewCurrent.get("CID");			
	    			System.out.println(latestCID);
	    			//update weights here
	    			 System.out.println("EXISTING:updated weights with to increase"+latestCID);
	    			 double[] redistribution = redistributeWeights(latestCID, currentAdjacency, currentWeights);
	    			 
	     	        updateCurrentsFuture.append("$set", new BasicDBObject().append("weights", redistribution).append("adjacencyList", currentAdjacency));
	    	
	    		}   			
	    		//System.out.println("Moving on 1");
	    		//Thirdly we get the context the user points to and point IT at the newly found/created context
	    		BasicDBObject contextPointUpdateQuery = new BasicDBObject().append("CID", users.findOne(new BasicDBObject().append("UID",uid)).get("currentContext"));
	
	    		 System.out.println("changing this");
	    		System.out.println(contextPointUpdateQuery.toString());
	    		contexts.update(contextPointUpdateQuery, updateCurrentsFuture);
	    		//System.out.println("Moving on 2");
	    		//Fourth we change the value of the users current context to point to the latest data 
	    		BasicDBObject updateUsersCurrent = new BasicDBObject().append("$set",new BasicDBObject().append("currentContext",latestCID));
	    		
	    		BasicDBObject userContextUpdateQuery = new BasicDBObject().append("UID", uid);
	    		users.update(userContextUpdateQuery, updateUsersCurrent );
	    		//System.out.println("Moving on 3");
	    		//finally return the services of the new currents future
	    		//this must return the CID that corresponds to the largest weight on the current contexts adjacency list
	    		int usersCurrentContextID  = (int) users.findOne(new BasicDBObject().append("UID",uid)).get("currentContext");
	    		DBObject nextContextAfterCurrent =  contexts.findOne(new BasicDBObject("CID", usersCurrentContextID), new BasicDBObject("adjacencyList", 1).append("weights",1));
	    		Object[] possibleFutureContexts =((BasicDBList) nextContextAfterCurrent.get("weights")).toArray();
	    		//System.out.println(possibleFutureContexts.toString());
	    		//find index of adjacency list with greatest corresponding weight
	    		int winnerIndex = 0;
	    		double largestWeight = 0;
	    		System.out.println("weights of current");
	    		for(int i = 0; i< possibleFutureContexts.length;i++){
	    			System.out.println(Double.valueOf(possibleFutureContexts[i].toString()));
	    		}
	    		for(int i = 0; i< possibleFutureContexts.length;i++){
	    			System.out.println("looking for best prediction");
	    			System.out.println(Double.valueOf(possibleFutureContexts[i].toString()));
	    			if(( Double.valueOf(possibleFutureContexts[i].toString()))>= largestWeight){
	    				winnerIndex = i;
	    				largestWeight = Double.valueOf(possibleFutureContexts[i].toString());
	    				System.out.println("LArgers weight"+ largestWeight);
	    			}
	    		}
	    		Object[] adj = ((BasicDBList) nextContextAfterCurrent.get("adjacencyList")).toArray();
	    		int predictionCID  =(int) adj[winnerIndex];
	    		System.out.println(predictionCID);
	    		DBObject arrayResults = contexts.findOne(new BasicDBObject("CID",predictionCID), new BasicDBObject("services", 1));
	    		//TODO if context has no services continue through linked list until one with services is found
	    		System.out.println("Moving on 6");
	    		System.out.println(arrayResults);
	    		BasicDBList serv = (BasicDBList) arrayResults.get("services");
	    	
	    		System.out.println("line worked");
	    		
	    		
	    		Object[] servArr = serv.toArray();
	    		int i = 0;
	    		System.out.println("Moving on 7");
	    		for(Object dbObj : servArr) {
	    			futureServices[i] = dbObj.toString();
	    			System.out.println(dbObj.toString());
	    			i++;
	    		  }
	    		lastServices = futureServices;
	    		System.out.println("Moving on 8");
	    		mongoClient.close();
	    		retArray = futureServices;
	    		
	    		
    		}else{
    			mongoClient.close();
    			retArray = lastServices;
    			
    		}
    		
    	}catch(Exception e){
    		System.out.println(e);
    	}
    	
    	return new Response(uid,retArray);
    	
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
	       
	        DBCollection macros = db.createCollection("macros", null);
	        BasicDBObject macroDoc = new BasicDBObject("MID", 1)
	        		.append("UID", 1)
	        		.append("name", "Default Macro")
	        		.append("actions", new String[]{})//add max id and add services list. 
	     	        .append("hours", new int[]{})
	     	        .append("minutes", new int[]{});
	        
	        BasicDBObject testc = new BasicDBObject("MID", 2)
	        		.append("UID", 1)
	        		.append("name", "test Macro")
	        		.append("actions", new String[]{})//add max id and add services list. 
	     	        .append("hours", new int[]{})
	     	        .append("minutes", new int[]{});
	     	     
	        macros.insert(testc);
	        macros.insert(macroDoc);
	        
	        String[] services = {"Undefined","Undefined","Undefined","Undefined","Undefined"};
	        
	        BasicDBObject doc = new BasicDBObject("UID", 1).
	        		append("Email", "joesph.joemc.mcevoy@gmail.com").//add max id and add services list. 
	     	        append("currentContext", 1).
	     	        append("maxCID", 1).
	     	        append("maxMID", 2);
	     	       //.append("visibleServices", services);
	     	     				
	        users.insert(doc);
	        System.out.println("Document inserted successfully");
	       
	        int[] adjacencyList = {1,0,0,0,0};
	        double[] weights= {0.001,0.0,0.0,0.0,0.0};
	        BasicDBObject baseContext = new BasicDBObject("CID", 1)
	        		.append("day",0)
	        		.append("hour",0)
	        		.append("services",services)
	     	        .append("adjacencyList", adjacencyList)
	     	        .append("weights",weights);
	     	    //    .append("relatedMacro", 1);
	        
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
    	System.out.println("Attempting weight distribition");
    	
    	double tempWeight =-1;
    	int tempAdj =-1;
    	int tempIndex = -1;
    	for(int i=0; i< adjacencyList.length;i++){
    			System.out.println("checking"+adjacencyList[i]+" against "+usedCID);
    		if(adjacencyList[i] == usedCID){
    		
    			System.out.println("increasing value");
    			weights[i] += 1.0/alpha;
    			System.out.println(weights[i]);
    			if(weights[i]>=1){
    				weights[i] =0.999;
    			}
    			tempIndex = i;
    			tempAdj = adjacencyList[i];
    			tempWeight = weights[i];
    		}else{
    			weights[i] -= (1.0/alpha)/(alpha-1.0);
    			if(weights[i]<=0){
    				weights[i] = 0;
    			}
    		}
    	}
    	if(tempAdj!= -1){
    		weights[tempIndex] = weights[weights.length-1];
    		adjacencyList[tempIndex] = adjacencyList[adjacencyList.length-1];
    		weights[weights.length-1] = tempWeight;
    		adjacencyList[adjacencyList.length-1] = tempAdj;
    	}
    	for(double x: weights){
    		System.out.println("weights after distribution");
    		System.out.println(x);
    		
    	}
    	return weights;
    }
    @RequestMapping("/editMacro")
    public int editMacro(@RequestParam(value="uid", defaultValue="0000") int uid,
    					 @RequestParam(value="mid", defaultValue="0000") int mid,
    					 @RequestParam(value="name", defaultValue="0000") String name,
    					 @RequestParam(value="actions", defaultValue="0000") String[] actions,
    					 @RequestParam(value="hours", defaultValue="0000") int[] hours,
    					 @RequestParam(value="minutes", defaultValue="0000") int[] minutes
    					 
    		){
    	System.out.println("attempting macro edit");
    	try{
    		MongoClient mongoClient = new MongoClient("localhost", 27017);
    		DB db = mongoClient.getDB( "test" );
    		DBCollection macros = db.getCollection("macros");
    		
    		BasicDBObject document = new BasicDBObject();
    		document.put("MID", mid);
    		macros.remove(document);
    		
    		BasicDBObject newMacro = new BasicDBObject("MID", mid)
            		.append("UID", uid)
            		.append("name", name)
            		.append("actions", actions)//add max id and add services list. 
         	        .append("hours", hours)
         	        .append("minutes", minutes);
        	
        	macros.insert(newMacro);
        	System.out.println("+++++++++++++++MAcro edited++++++++++++++");
        	System.out.println(name);
        	mongoClient.close();
    	}catch(Exception E){	
    		
    	}
    	
    	
    	return 1;
    }
    @RequestMapping("/createNewMacro")
    public void createNewMacro(@RequestParam(value="uid", defaultValue="0000") int uid,
    		@RequestParam(value="name", defaultValue="0000") String name){
    	
    	try{
    	MongoClient mongoClient = new MongoClient("localhost", 27017);
		DB db = mongoClient.getDB( "test" );
		DBCollection macros = db.getCollection("macros");
		DBCollection users = db.getCollection("users");
		
		DBObject user =  users.findOne( new BasicDBObject().append("UID",uid));  //.get("currentContext");
		int maxMID = (int) user.get("maxMID");
		BasicDBObject updateUsersCurrent = new BasicDBObject().append("$set",new BasicDBObject().append("maxMID",++maxMID));
		BasicDBObject userContextUpdateQuery = new BasicDBObject().append("UID", uid);
		users.update(userContextUpdateQuery, updateUsersCurrent );
		
		System.out.println(maxMID);
		//TODO sort out max id issue	
    	BasicDBObject newMacro = new BasicDBObject("MID", maxMID)
        		.append("UID", uid)
        		.append("name", name)
        		.append("actions", new String[]{})//add max id and add services list. 
     	        .append("hours", new int[]{})
     	        .append("minutes", new int[]{});
    	
    	macros.insert(newMacro);
    	System.out.println("+++++++++++++++NEW MACRO CREATED+++++++++++");
    	
    	mongoClient.close();
    	}catch(Exception E){
    		System.out.println(E);
    	}
    }
 
    
    @RequestMapping("/getMacros")
    public Map<String,DBObject> getMacros(@RequestParam(value="uid", defaultValue="0000") int uid){
    	Cursor macroCursor;
    	Map<String, DBObject> macroMap =new HashMap<String, DBObject>();
    	try{
    		MongoClient mongoClient = new MongoClient("localhost", 27017);
    		DB db = mongoClient.getDB( "test" );
    		DBCollection macros = db.getCollection("macros");
    		macroCursor =  macros.find(new BasicDBObject("UID", uid));
    		
    		int i = 0;
    		while(macroCursor.hasNext()){
    			macroMap.put("macro"+i, macroCursor.next());
    			i++;
    		}
    		
    		mongoClient.close();
    		return macroMap;
    	}catch(Exception e){
    		macroMap = null;
    	}
    	return macroMap;
    }	
    @RequestMapping("/lightOn")
    public boolean lightOn(){
    	
    	Enumeration portList;
       CommPortIdentifier portId;
       String messageString ="1";
        if(serialPort!= null){
        	serialPort.close();
        }
        OutputStream outputStream =null;

    	portList = CommPortIdentifier.getPortIdentifiers();

        while (portList.hasMoreElements()) {
            portId = (CommPortIdentifier) portList.nextElement();
            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                 if (portId.getName().equals("COM3")) {
                //if (portId.getName().equals("/dev/term/a")) {
                    try {
                        serialPort = (SerialPort) portId.open("SimpleWriteApp", 3000);
                    } catch (PortInUseException e) {
                    	System.out.println(e);
                    }
                    try {
                    	
                        outputStream = serialPort.getOutputStream();
                    } catch (IOException e) {}
                    try {
                        serialPort.setSerialPortParams(9600,
                            SerialPort.DATABITS_8,
                            SerialPort.STOPBITS_1,
                            SerialPort.PARITY_NONE);
                    } catch (UnsupportedCommOperationException e) {}
                    try {
                    	System.out.println("sending mssage"+messageString);
                        outputStream.write(messageString.getBytes());
                        
                    } catch (IOException e) {}
                }
            }
        }
        
    	return true;
    }
    @RequestMapping("/lightOff")
    public boolean lightOff(){
    	if(serialPort!= null){
    		serialPort.close();
    	}
    	return true;
    	
    }
    
}