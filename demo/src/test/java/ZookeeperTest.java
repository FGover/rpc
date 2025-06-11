import com.fg.zookeeper.MyWatcher;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class ZookeeperTest {
    ZooKeeper zooKeeper;

    @Before
    public void createZk() {
        // 定义连接参数
        String connectString = "192.168.116.100:2181";
        // 定义超时时间
        int timeout = 10000;
        try {
            zooKeeper = new ZooKeeper(connectString, timeout, new MyWatcher());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testCreateNode() {
        try {
            String result = zooKeeper.create("/fg", "fg是帅哥".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT);
            System.out.println(result);
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                if (zooKeeper != null) {
                    zooKeeper.close();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testGetNode() {
        try {
            byte[] data = zooKeeper.getData("/fg", false, null);
            System.out.println(new String(data));
        } catch (InterruptedException | KeeperException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testDeleteNode() {
        try {
            zooKeeper.delete("/fg", -1);
        } catch (InterruptedException | KeeperException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testExistsNode() {
        try {
            Stat stat = zooKeeper.exists("/fg", false);
            System.out.println(stat);
        } catch (InterruptedException | KeeperException e) {
            throw new RuntimeException(e);
        }
    }
}
