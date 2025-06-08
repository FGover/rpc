import java.io.*;

class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private int age;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public String toString() {
        return "User [name=" + name + ", age=" + age + "]";
    }
}

public class SerializableTest {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        // 创建对象
        User user = new User("fg", 23);
        // 序列化：对象->字节数组
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(user);
        objectOutputStream.flush();
        byte[] serializedData = byteArrayOutputStream.toByteArray();
        System.out.println("序列化完成，字节数组长度：" + serializedData.length);
        // 反序列化：字节数组->对象
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serializedData);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        User deserializedUser = (User) objectInputStream.readObject();
        System.out.println("反序列化完成：" + deserializedUser);
    }
}