package local.handson;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class LabTest {
    static Lab lab;
    @BeforeAll static void init() throws Exception { lab=new Lab(); }
    @Test void normalAndRecovery() throws Exception {
        assertEquals(true,lab.run("normal","anchor").get("accepted"));
        assertEquals(false,lab.run("normal","other").get("accepted"));
        assertEquals(true,lab.run("normal","anchor").get("accepted"));
    }
    @Test void alteredInputsRejected() throws Exception {
        for(String s:new String[]{"tampered","expired","unregistered","token-key"}) assertEquals(false,lab.run(s,"anchor").get("accepted"),s);
    }
    @Test void invalidChoiceRejected() { assertThrows(IllegalArgumentException.class,()->lab.run("whatever","anchor")); }
}
