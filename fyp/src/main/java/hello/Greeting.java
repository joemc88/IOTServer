package hello;

public class Greeting {

    private final long id;
    private final String content;

    private final String UID;
    private final String time;
    private final String location;
    private final String day;
    private final String holiday;
    private final String wifi;
    private final String services;
    private final String futureContext;
    
    
    
    public Greeting(long id, String content,
    		String UID, String time, String location, String day, String holiday, String wifi, String services, String future) {
        this.id = id;
        this.content = content;
        
        this.UID = UID;
        this.time = time;
        this.location = location;
        this.day = day;
        this.holiday = holiday;
        this.wifi = wifi;
        this.services = services;
        this.futureContext = services;
        		
    }

    public long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }
    
    
    
    public String getUID(){
    	return UID;
    }
    public String getTime(){
    	return time;
    }
    public String getLocation(){
    	return location;
    }
    public String getDay(){
    	return day;
    }
    public String getHoliday(){
    	return holiday;
    }
    public String getWifi(){
    	return wifi;
    }
    public String getServices(){
    	return services;
    }
    public String getFuture(){
    	return futureContext;
    }
}