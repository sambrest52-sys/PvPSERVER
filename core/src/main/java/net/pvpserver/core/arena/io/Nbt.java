package net.pvpserver.core.arena.io;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal reader for Minecraft's NBT format (gzip-compressed or raw), enough for schematic files. Compounds become
 * {@code Map<String, Object>}, lists {@code List<Object>}, arrays {@code byte[]/int[]/long[]} and numbers their boxed
 * Java types. No Bukkit dependency.
 */
public final class Nbt {

    private static final int MAX_DEPTH = 512;
    private static final int MAX_ARRAY = 64 * 1024 * 1024;

    private Nbt() {
    }

    /**
     * Reads the root tag of an NBT stream.
     *
     * @param in stream (gzip detected automatically)
     * @return root compound
     * @throws IOException when the data is not NBT or is malformed
     */
    public static Map<String, Object> read(InputStream in) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(in);
        buffered.mark(2);
        int first = buffered.read();
        int second = buffered.read();
        buffered.reset();
        InputStream source = first == 0x1f && second == 0x8b ? new GZIPInputStream(buffered) : buffered;
        DataInputStream data = new DataInputStream(source);
        int type = data.readUnsignedByte();
        if (type != 10) {
            throw new IOException("Not an NBT compound (tag type " + type + ")");
        }
        data.readUTF();
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) payload(data, type, 0);
        return root;
    }

    private static Object payload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nested too deeply");
        }
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                byte[] bytes = new byte[length(in)];
                in.readFully(bytes);
                return bytes;
            }
            case 8:
                return in.readUTF();
            case 9: {
                int elementType = in.readUnsignedByte();
                int size = length(in);
                List<Object> list = new ArrayList<>(Math.min(size, 4096));
                for (int i = 0; i < size; i++) {
                    list.add(payload(in, elementType, depth + 1));
                }
                return list;
            }
            case 10: {
                Map<String, Object> compound = new LinkedHashMap<>();
                while (true) {
                    int childType = in.readUnsignedByte();
                    if (childType == 0) {
                        return compound;
                    }
                    String name = in.readUTF();
                    compound.put(name, payload(in, childType, depth + 1));
                }
            }
            case 11: {
                int[] ints = new int[length(in)];
                for (int i = 0; i < ints.length; i++) {
                    ints[i] = in.readInt();
                }
                return ints;
            }
            case 12: {
                long[] longs = new long[length(in)];
                for (int i = 0; i < longs.length; i++) {
                    longs[i] = in.readLong();
                }
                return longs;
            }
            default:
                throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int length(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_ARRAY) {
            throw new IOException("Invalid NBT array length " + length);
        }
        return length;
    }

    // ------------------------------------------------------------------ typed access helpers

    /**
     * @param compound compound
     * @param key key
     * @return nested compound or null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> compound(Map<String, Object> compound, String key) {
        return compound != null && compound.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /**
     * @param compound compound
     * @param key key
     * @return list or an empty list
     */
    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> compound, String key) {
        return compound != null && compound.get(key) instanceof List<?> list ? (List<Object>) list : List.of();
    }

    /**
     * @param compound compound
     * @param key key
     * @param fallback value when missing
     * @return integer value of any numeric tag
     */
    public static int integer(Map<String, Object> compound, String key, int fallback) {
        return compound != null && compound.get(key) instanceof Number n ? n.intValue() : fallback;
    }
}
