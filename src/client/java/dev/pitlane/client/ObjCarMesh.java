package dev.pitlane.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.pitlane.PitlaneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Wavefront OBJ renderer for the supplied Range Rover mesh.
 * Geometry is loaded once from the mod resources; material wire colors are
 * decoded from the original export's wire_RRRGGGBBB material names.
 */
final class ObjCarMesh {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(PitlaneMod.MOD_ID, "models/entity/range_rover.obj");
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(PitlaneMod.MOD_ID, "textures/entity/white.png");
    private static final float SCALE = 0.000022f;
    private static volatile ObjCarMesh instance;

    private final List<Triangle> triangles;

    private ObjCarMesh(List<Triangle> triangles) {
        this.triangles = triangles;
    }

    static ObjCarMesh get() {
        ObjCarMesh loaded = instance;
        if (loaded != null) return loaded;
        synchronized (ObjCarMesh.class) {
            if (instance == null) instance = load();
            return instance;
        }
    }

    void submit(PoseStack poseStack, SubmitNodeCollector collector) {
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WHITE), this::render);
    }

    void render(PoseStack.Pose pose, VertexConsumer buffer) {
        for (Triangle triangle : triangles) {
            emit(pose, buffer, triangle.a);
            emit(pose, buffer, triangle.b);
            emit(pose, buffer, triangle.c);
        }
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, Vertex vertex) {
        buffer.addVertex(pose, vertex.x * SCALE, vertex.y * SCALE, -vertex.z * SCALE)
                .setColor(vertex.red, vertex.green, vertex.blue, 255)
                .setUv(0.5f, 0.5f)
                .setOverlay(0)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, vertex.nx, vertex.ny, -vertex.nz);
    }

    private static ObjCarMesh load() {
        List<Vector3f> positions = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<Triangle> triangles = new ArrayList<>();
        int red = 210, green = 210, blue = 210;
        try (var resource = Minecraft.getInstance().getResourceManager().getResource(MODEL).orElseThrow(
                () -> new IllegalStateException("Missing car OBJ resource: " + MODEL));
             var reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("v ")) {
                    String[] p = line.split("\\s+");
                    positions.add(new Vector3f(Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])));
                } else if (line.startsWith("vn ")) {
                    String[] p = line.split("\\s+");
                    normals.add(new Vector3f(Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])));
                } else if (line.startsWith("vt ")) {
                    String[] p = line.split("\\s+");
                    uvs.add(new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2])});
                } else if (line.startsWith("usemtl ")) {
                    int[] rgb = materialColor(line.substring(7).trim());
                    red = rgb[0]; green = rgb[1]; blue = rgb[2];
                } else if (line.startsWith("f ")) {
                    String[] refs = line.substring(2).trim().split("\\s+");
                    if (refs.length < 3) continue;
                    Vertex first = vertex(refs[0], positions, uvs, normals, red, green, blue);
                    for (int i = 1; i < refs.length - 1; i++) {
                        triangles.add(new Triangle(first,
                                vertex(refs[i], positions, uvs, normals, red, green, blue),
                                vertex(refs[i + 1], positions, uvs, normals, red, green, blue)));
                    }
                }
            }
            PitlaneMod.LOGGER.info("Loaded Range Rover OBJ: {} triangles", triangles.size());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load Range Rover OBJ model", exception);
        }
        return new ObjCarMesh(List.copyOf(triangles));
    }

    private static Vertex vertex(String reference, List<Vector3f> positions, List<float[]> uvs,
                                 List<Vector3f> normals, int red, int green, int blue) {
        String[] parts = reference.split("/", -1);
        Vector3f p = positions.get(index(parts[0], positions.size()));
        Vector3f n = parts.length > 2 && !parts[2].isEmpty()
                ? normals.get(index(parts[2], normals.size())) : new Vector3f(0, 1, 0);
        return new Vertex(p.x, p.y, p.z, n.x, n.y, n.z, red, green, blue);
    }

    private static int index(String value, int size) {
        int parsed = Integer.parseInt(value);
        return parsed < 0 ? size + parsed : parsed - 1;
    }

    private static int[] materialColor(String material) {
        if (material.startsWith("wire_") && material.length() >= 14) {
            try {
                return new int[]{
                        Integer.parseInt(material.substring(5, 8)),
                        Integer.parseInt(material.substring(8, 11)),
                        Integer.parseInt(material.substring(11, 14))
                };
            } catch (NumberFormatException ignored) {
                // Fall through to a neutral body color.
            }
        }
        return new int[]{210, 210, 210};
    }

    private record Vertex(float x, float y, float z, float nx, float ny, float nz,
                          int red, int green, int blue) {}
    private record Triangle(Vertex a, Vertex b, Vertex c) {}
}
