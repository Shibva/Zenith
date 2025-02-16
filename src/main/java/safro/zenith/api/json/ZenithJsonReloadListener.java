package safro.zenith.api.json;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import safro.zenith.Zenith;
import safro.zenith.network.ReloadListenerPacket;
import safro.zenith.util.ZenithUtil;

public abstract class ZenithJsonReloadListener<V extends ZenithJsonReloadListener.TypeKeyed<V>> extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {

    public static final Map<String, ZenithJsonReloadListener<?>> SYNC_REGISTRY = new HashMap<>();

    public static final ResourceLocation DEFAULT = new ResourceLocation("default");

    protected final Logger logger;
    protected final String path;
    protected final boolean synced;
    protected final boolean subtypes;
    protected final BiMap<ResourceLocation, SerializerBuilder<V>.Serializer> serializers = HashBiMap.create();

    protected Map<ResourceLocation, V> registry = ImmutableMap.of();

    private final Map<ResourceLocation, V> staged = new HashMap<>();

    public ZenithJsonReloadListener(Logger logger, String path, boolean synced, boolean subtypes) {
        super(new GsonBuilder().setLenient().create(), path);
        this.logger = logger;
        this.path = path;
        this.synced = synced;
        this.subtypes = subtypes;
        this.registerBuiltinSerializers();
        if (this.serializers.isEmpty()) throw new RuntimeException("Attempted to create a json reload listener for " + path + " with no top-level serializers!");
        if (synced) {
            if (SYNC_REGISTRY.containsKey(path)) throw new RuntimeException("Attempted to create a synced json reload listener for " + path + " but one already exists!");
            SYNC_REGISTRY.put(path, this);
        }
    }

    @Override
    protected final void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        this.beginReload();
        objects.forEach((key, ele) -> {
            try {
                if (checkAndLogEmpty(ele, key, this.path, this.logger) && checkConditions(ele, key, this.path, this.logger)) {
                    JsonObject obj = ele.getAsJsonObject();
                    SerializerBuilder<V>.Serializer serializer;
                    if (this.subtypes && obj.has("type")) {
                        ResourceLocation type = new ResourceLocation(obj.get("type").getAsString());
                        serializer = this.serializers.get(type);
                        if (serializer == null) throw new RuntimeException("Attempted to deserialize a " + this.path + " with type " + type + " but no serializer exists!");
                    } else {
                        serializer = this.serializers.get(DEFAULT);
                    }
                    V deserialized = serializer.deserialize(obj);
                    deserialized.setId(key);
                    deserialized.setSerializer(serializer);
                    Preconditions.checkNotNull(deserialized.getId(), "A " + this.path + " with id " + key + " failed to set ID.");
                    Preconditions.checkNotNull(deserialized.getSerializer(), "A " + this.path + " with id " + key + " failed to set serializer.");
                    this.register(key, deserialized);
                }
            } catch (Exception e) {
                this.logger.error("Failed parsing {} file {}.", this.path, key);
                e.printStackTrace();
            }
        });
        this.onReload();
    }

    /**
     * Add all default serializers to this reload listener.
     * This should be a series of calls to {@link }
     */
    protected abstract void registerBuiltinSerializers();

    /**
     * Called when this manager begins reloading all items.
     * Should handle clearing internal data caches.
     */
    protected void beginReload() {
        this.registry = new HashMap<>();
    }

    /**
     * Called after this manager has finished reloading all items.
     * Should handle any info logging, and data immutability.
     */
    protected void onReload() {
        this.registry = ImmutableMap.copyOf(this.registry);
        this.logger.info("Registered {} {}.", this.registry.size(), this.path);
    }

    public final void registerSerializer(ResourceLocation id, SerializerBuilder<V> serializer) {
        if (this.subtypes) {
            if (this.serializers.containsKey(id)) throw new RuntimeException("Attempted to register a " + this.path + " serializer with id " + id + " but one already exists!");
            if (this.serializers.isEmpty() && id != DEFAULT) this.serializers.put(DEFAULT, serializer.build(this.synced));
            this.serializers.put(id, serializer.build(this.synced));
        } else {
            if (!this.serializers.isEmpty()) throw new RuntimeException("Attempted to register a " + this.path + " serializer with id " + id + " but subtypes are not supported!");
            this.serializers.put(DEFAULT, serializer.build(this.synced));
        }
    }

    public final void sync(ServerPlayer player) {
        if (player == null) {
            ReloadListenerPacket.Start.sendToAll(this.path);
            this.registry.forEach((k, v) -> {
                ReloadListenerPacket.Content.sendToAll(this.path, k, v);
            });
            ReloadListenerPacket.End.sendToAll(this.path);
        } else {
            ReloadListenerPacket.Start.sendTo(player, this.path);
            this.registry.forEach((k, v) -> {
                ReloadListenerPacket.Content.sendTo(player, this.path, k, v);
            });
            ReloadListenerPacket.End.sendTo(player, this.path);
        }
    }

    protected <T extends V> void validateItem(T item) {
        Preconditions.checkNotNull(item);
    }

    /**
     * Registers a single item of this type to the registry during reload.
     * You can override this method to process things a bit differently.
     */
    protected <T extends V> void register(ResourceLocation key, T item) {
        this.registry.put(key, item);
    }

    /**
     * @return An immutable view of all keys registered for this type.
     */
    public Set<ResourceLocation> getKeys() {
        return this.registry.keySet();
    }

    /**
     * @return An immutable view of all items registered for this type.
     */
    public Collection<V> getValues() {
        return this.registry.values();
    }

    /**
     * @return The item associated with this key, or null.
     */
    @Nullable
    public V getValue(ResourceLocation key) {
        return this.getOrDefault(key, null);
    }

    /**
     * @return The item associated with this key, or the default value.
     */
    public V getOrDefault(ResourceLocation key, V defValue) {
        return this.registry.getOrDefault(key, defValue);
    }

    @Override
    public ResourceLocation getFabricId() {
        return new ResourceLocation(Zenith.MODID, this.path);
    }

    /**
     * Checks if an item is empty, and if it is, returns false and logs the key.
     */
    public static boolean checkAndLogEmpty(JsonElement e, ResourceLocation id, String type, Logger logger) {
        String s = e.toString();
        if (s.isEmpty() || s.equals("{}")) {
            logger.debug("Ignoring {} item with id {} as it is empty.", type, id);
            return false;
        }
        return true;
    }

    public static boolean checkConditions(JsonElement e, ResourceLocation id, String type, Logger logger) {
        if (e.isJsonObject() && !ZenithUtil.processConditions(e.getAsJsonObject(), "conditions")) {
            logger.debug("Skipping loading {} item with id {} as it's conditions were not met", type, id);
            return false;
        }
        return true;
    }

    public static interface TypeKeyed<V extends TypeKeyed<V>> {
        void setId(ResourceLocation id);

        void setSerializer(SerializerBuilder<V>.Serializer serializer);

        ResourceLocation getId();

        SerializerBuilder<V>.Serializer getSerializer();
    }

    public static abstract class TypeKeyedBase<V extends TypeKeyed<V>> implements TypeKeyed<V> {
        protected ResourceLocation id;
        protected SerializerBuilder<V>.Serializer serializer;

        @Override
        public void setId(ResourceLocation id) {
            if (this.id != null) throw new UnsupportedOperationException();
            this.id = id;
        }

        @Override
        public void setSerializer(SerializerBuilder<V>.Serializer serializer) {
            if (this.serializer != null) throw new UnsupportedOperationException();
            this.serializer = serializer;
        }

        @Override
        public ResourceLocation getId() {
            return this.id;
        }

        @Override
        public SerializerBuilder<V>.Serializer getSerializer() {
            return this.serializer;
        }
    }

    public static void initSync(String path) {
        SYNC_REGISTRY.computeIfPresent(path, (k, v) -> {
            v.staged.clear();
            return v;
        });
    }

    public static <V extends TypeKeyed<V>> void writeItem(String path, V value, FriendlyByteBuf buf) {
        SYNC_REGISTRY.computeIfPresent(path, (k, v) -> {
            ResourceLocation serId = v.serializers.inverse().get(value.getSerializer());
            buf.writeResourceLocation(serId);
            value.getSerializer().serialize(value, buf);
            return v;
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <V extends TypeKeyed<V>> V readItem(String path, ResourceLocation key, FriendlyByteBuf buf) {
        var listener = SYNC_REGISTRY.get(path);
        if (listener == null) throw new RuntimeException("Received sync packet for unknown registry!");
        var serializer = listener.serializers.get(buf.readResourceLocation());
        V v = (V) serializer.deserialize(buf);
        v.setId(key);
        v.setSerializer((SerializerBuilder<V>.Serializer) serializer);
        return v;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <V extends TypeKeyed<V>> void acceptItem(String path, ResourceLocation key, V value) {
        SYNC_REGISTRY.computeIfPresent(path, (k, v) -> {
            ((Map) v.staged).put(key, value);
            return v;
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <V extends TypeKeyed<V>> void endSync(String path) {
        SYNC_REGISTRY.computeIfPresent(path, (k, v) -> {
            v.beginReload();
            v.staged.forEach(((ZenithJsonReloadListener) v)::register);
            v.onReload();
            return v;
        });
    }

}
