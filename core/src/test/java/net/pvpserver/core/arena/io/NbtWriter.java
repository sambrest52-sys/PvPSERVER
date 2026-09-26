package net.pvpserver.core.arena.io;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Test helper that writes NBT (gzip) the way schematic tools do, so readers can be tested against real bytes.
 */
public final class NbtWriter {

    private NbtWriter() {
    }

    /**
     * @param rootName root tag name (e.g. "Schematic")
     * @param root compound contents
     * @return gzip NBT bytes
     */
    public static byte[] write(String rootName, Map<String, Object> root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF(rootName);
            payload(out, root);
        }
        return bytes.toByteArray();
    }

    private static int type(Object value) {
        if (value instanceof Byte) {
            return 1;
        } else if (value instanceof Short) {
            return 2;
        } else if (value instanceof Integer) {
            return 3;
        } else if (value instanceof Long) {
            return 4;
        } else if (value instanceof Float) {
            return 5;
        } else if (value instanceof Double) {
            return 6;
        } else if (value instanceof byte[]) {
            return 7;
        } else if (value instanceof String) {
            return 8;
        } else if (value instanceof List<?>) {
            return 9;
        } else if (value instanceof Map<?, ?>) {
            return 10;
        } else if (value instanceof int[]) {
            return 11;
        } else if (value instanceof long[]) {
            return 12;
        }
        throw new IllegalArgumentException("Unsupported NBT value " + value);
    }

    @SuppressWarnings("unchecked")
    private static void payload(DataOutputStream out, Object value) throws IOException {
        switch (type(value)) {
            case 1 -> out.writeByte((Byte) value);
            case 2 -> out.writeShort((Short) value);
            case 3 -> out.writeInt((Integer) value);
            case 4 -> out.writeLong((Long) value);
            case 5 -> out.writeFloat((Float) value);
            case 6 -> out.writeDouble((Double) value);
            case 7 -> {
                byte[] bytes = (byte[]) value;
                out.writeInt(bytes.length);
                out.write(bytes);
            }
            case 8 -> out.writeUTF((String) value);
            case 9 -> {
                List<Object> list = (List<Object>) value;
                out.writeByte(list.isEmpty() ? 0 : type(list.get(0)));
                out.writeInt(list.size());
                for (Object element : list) {
                    payload(out, element);
                }
            }
            case 10 -> {
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                    out.writeByte(type(entry.getValue()));
                    out.writeUTF(entry.getKey());
                    payload(out, entry.getValue());
                }
                out.writeByte(0);
            }
            case 11 -> {
                int[] ints = (int[]) value;
                out.writeInt(ints.length);
                for (int i : ints) {
                    out.writeInt(i);
                }
            }
            case 12 -> {
                long[] longs = (long[]) value;
                out.writeInt(longs.length);
                for (long l : longs) {
                    out.writeLong(l);
                }
            }
            default -> throw new IllegalStateException();
        }
    }

    /**
     * Encodes palette indices as the varint byte array Sponge schematics use.
     *
     * @param indices indices
     * @return varint bytes
     */
    public static byte[] varints(int[] indices) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int value : indices) {
            while ((value & ~0x7F) != 0) {
                out.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            out.write(value);
        }
        return out.toByteArray();
    }
}
