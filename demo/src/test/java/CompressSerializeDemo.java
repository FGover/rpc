import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

class User1 implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private int age;

    public User1(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public String toString() {
        return "User1 [name=" + name + ", age=" + age + "]";
    }
}


public class CompressSerializeDemo {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        User1 user1 = new User1("fg", 23);
        // 序列化并压缩
        byte[] compressedBytes = serializeAndCompress(user1);
        System.out.println("压缩后的字节长度：" + compressedBytes.length);
        // 解压缩并反序列化
        User1 deserializedUser = (User1) decompressAndDeserialize(compressedBytes);
        System.out.println("解压后的对象：" + deserializedUser);
    }

    // 序列化 + 压缩
    public static byte[] serializeAndCompress(Object obj) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
             ObjectOutputStream objectOut = new ObjectOutputStream(gzipOut)) {
            objectOut.writeObject(obj);
        }
        return byteOut.toByteArray();
    }

    // 解压缩 + 反序列化
    public static Object decompressAndDeserialize(byte[] compressedBytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteIn = new ByteArrayInputStream(compressedBytes);
        try (GZIPInputStream gzipIn = new GZIPInputStream(byteIn);
             ObjectInputStream objectIn = new ObjectInputStream(gzipIn)) {
            return objectIn.readObject();
        }
    }
}
