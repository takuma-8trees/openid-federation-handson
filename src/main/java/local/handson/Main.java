package local.handson;
import com.sun.net.httpserver.*;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class Main {
    public static void main(String[] args) throws Exception {
        Lab lab=new Lab();
        var server=HttpServer.create(new InetSocketAddress(System.getenv().getOrDefault("BIND_ADDRESS","127.0.0.1"),Integer.parseInt(System.getenv().getOrDefault("PORT","8080"))),0);
        server.createContext("/",e->{try {
            if(!e.getRequestMethod().equals("GET")) { send(e,405,"text/plain","Method not allowed");return; }
            if(e.getRequestURI().getPath().equals("/api/experiment")) {
                Map<String,String> q=new HashMap<>();
                for(String part:Optional.ofNullable(e.getRequestURI().getRawQuery()).orElse("").split("&")) { var p=part.split("=",2); if(p.length==2) q.put(p[0],URLDecoder.decode(p[1],StandardCharsets.UTF_8)); }
                send(e,200,"application/json",JSONObjectUtils.toJSONString(lab.run(q.getOrDefault("scenario","normal"),q.getOrDefault("trust","anchor"))));
            } else if(e.getRequestURI().getPath().equals("/")) {
                try(var s=Main.class.getResourceAsStream("/index.html")){send(e,200,"text/html",new String(s.readAllBytes(),StandardCharsets.UTF_8));}
            } else send(e,404,"text/plain","Not found");
        } catch(IllegalArgumentException ex){send(e,400,"application/json","{\"error\":\"Invalid experiment\"}");}
          catch(Exception ex){send(e,500,"application/json","{\"error\":\"Experiment failed\"}");}});
        server.start();System.out.println("Federation lab listening on "+server.getAddress().getPort());
    }
    static void send(HttpExchange e,int status,String type,String body) throws java.io.IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");e.getResponseHeaders().set("Cache-Control","no-store");
        e.getResponseHeaders().set("X-Content-Type-Options","nosniff");e.sendResponseHeaders(status,bytes.length);try(var s=e.getResponseBody()){s.write(bytes);}
    }
}

