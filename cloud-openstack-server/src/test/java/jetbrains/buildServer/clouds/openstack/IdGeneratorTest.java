package jetbrains.buildServer.clouds.openstack;

import org.testng.Assert;
import org.testng.annotations.Test;

public class IdGeneratorTest {

    @Test
    public void testGenerate() {
        IdGenerator generator = new IdGenerator();
        String lastId = "";
        long d = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            String newId = generator.next();
            System.out.println(lastId + "/" + newId);
            Assert.assertNotEquals(lastId, newId);
            Assert.assertTrue(d <= Long.parseLong(newId));
            lastId = newId;
        }
    }
}
