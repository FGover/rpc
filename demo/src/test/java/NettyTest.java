import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class NettyTest {
    // 零拷贝实现方式
    @Test
    public void testByteBuf() {
        ByteBuf header = Unpooled.buffer();   // 创建一个未池化的缓冲区
        header.writeBytes("HEAD".getBytes(StandardCharsets.UTF_8));
        ByteBuf body = Unpooled.buffer();
        body.writeBytes("BODY".getBytes(StandardCharsets.UTF_8));
        CompositeByteBuf httpBuf = Unpooled.compositeBuffer();
        httpBuf.addComponents(true, header, body);  // true参数表示在CompositeByteBuf中共享这两个ByteBuf实例
        System.out.println(httpBuf.toString(StandardCharsets.UTF_8));  // HEADBODY
    }

    @Test
    public void testWrapper() {
        byte[] buf1 = "1234".getBytes(StandardCharsets.UTF_8);  // 将字符串转换为字节数组
        byte[] buf2 = "ABCD".getBytes(StandardCharsets.UTF_8);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(buf1, buf2);  // 合并字节数组
        byte[] all = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(all);
        System.out.println(new String(all, StandardCharsets.UTF_8));  // 1234ABCD
    }

    @Test
    public void testSlice() {
        ByteBuf byteBuf = Unpooled.buffer(10);
        byteBuf.writeBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        ByteBuf header = byteBuf.slice(0, 5);
        ByteBuf body = byteBuf.slice(5, 5);
        // 读取header
        while (header.isReadable()) {
            System.out.print(header.readByte() + " ");
        }
        System.out.println();
        // 读取body
        while (body.isReadable()) {
            System.out.print(body.readByte() + " ");
        }
    }
}
