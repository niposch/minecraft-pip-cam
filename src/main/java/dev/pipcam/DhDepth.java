package dev.pipcam;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Adds DH foreground depth to the PiP before Minecraft draws its full-detail terrain. */
public final class DhDepth {
    private static final String VSH = """
            #version 150
            out vec2 uv;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                uv = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FSH = """
            #version 150
            uniform sampler2D LodDepth;
            uniform sampler2D Coverage;
            uniform vec2 LodProjectionZ;
            uniform vec2 WorldProjectionZ;
            uniform vec2 FocalScale;
            uniform mat3 ViewToWorld;
            uniform ivec2 CameraChunk;
            uniform vec2 CameraOffset;
            uniform ivec2 PlayerOrigin;
            uniform ivec2 RetainedOrigin;
            uniform int CoverageSize;
            in vec2 uv;

            bool hasSurface(ivec2 chunk, ivec2 origin, int columnOffset) {
                ivec2 cell = chunk - origin;
                return all(greaterThanEqual(cell, ivec2(0))) && all(lessThan(cell, ivec2(CoverageSize)))
                    && texelFetch(Coverage, cell + ivec2(columnOffset, 0), 0).r > 0.5;
            }

            void main() {
                float depth = texelFetch(LodDepth, ivec2(gl_FragCoord.xy), 0).r;
                bool reverseZ = LodProjectionZ.y > 0.0;
                if (reverseZ ? depth <= 0.0 : depth >= 1.0) discard;
                float distance = LodProjectionZ.y / (depth * 2.0 - 1.0 + LodProjectionZ.x);
                if (!(distance > 0.0)) discard;
                float worldDepth = (WorldProjectionZ.y / distance - WorldProjectionZ.x) * 0.5 + 0.5;
                if (worldDepth < 0.0 || worldDepth >= 1.0) discard;
                vec3 eye = vec3((uv * 2.0 - 1.0) / FocalScale, -1.0) * distance;
                vec2 offset = (ViewToWorld * eye).xz + CameraOffset;
                ivec2 chunk = CameraChunk + ivec2(floor(offset / 16.0));
                // Keep DH as a color fallback here, but let full-detail terrain replace it.
                if (hasSurface(chunk, PlayerOrigin, 0)
                        || hasSurface(chunk, RetainedOrigin, CoverageSize)) discard;
                gl_FragDepth = worldDepth;
            }
            """;

    private static final Matrix3f TMP_MAT3 = new Matrix3f();

    private static Field dhProjection, mcProjection, m00, m11, m22, m23;
    private static int program, vao, coverageTexture, allocatedSize, coverageSize;
    private static int lodZ, worldZ, focalScale, viewToWorld, cameraChunk, cameraOffset;
    private static int playerOrigin, retainedOrigin, sizeLocation;
    private static ByteBuffer coverage;
    private static boolean failed, copied;

    // Coverage rebuild throttling: only scan chunks when origins/ranges change or every ~250ms
    private static int lastPx = Integer.MIN_VALUE, lastPz = Integer.MIN_VALUE;
    private static int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
    private static int lastPlayerRange = -1, lastRetainedRange = -1, lastRetainedCount = -1;
    private static long lastCoverageNanos;

    private DhDepth() {}

    static void beginFrame() {
        copied = false;
    }

    public static boolean copiedThisFrame() {
        return copied;
    }

    public static void copy(RenderTarget target, int lodDepth, Object params) {
        if (failed || target == null || lodDepth <= 0
                || lodDepth == target.getDepthTextureId()) return;
        try {
            if (program == 0) init(params.getClass());
            draw(target, lodDepth, dhProjection.get(params), mcProjection.get(params));
            copied = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            PipCamMod.LOGGER.warn("Could not transfer Distant Horizons depth to the PiP", e);
        }
    }

    private static void draw(RenderTarget target, int depth, Object dh, Object mc) throws IllegalAccessException {
        var camera = PipLevelRenderer.camera();
        var pos = camera.getPosition();
        var game = Minecraft.getInstance();
        if (game.level == null || game.player == null) return;
        var player = game.player.chunkPosition();
        int cx = Mth.floor(pos.x) >> 4, cz = Mth.floor(pos.z) >> 4;
        int playerRange = game.options.getEffectiveRenderDistance() + 3;
        int retainedCount = ChunkRetention.retainedCount();
        int retainedRange = retainedCount == 0 ? 0 : PipState.INSTANCE.retainRadiusChunks;
        int range = Math.max(playerRange, retainedRange);
        int size = range * 2 + 1;

        long now = System.nanoTime();
        boolean dirty = coverage == null || coverageSize != size
                || player.x != lastPx || player.z != lastPz
                || cx != lastCx || cz != lastCz
                || playerRange != lastPlayerRange || retainedRange != lastRetainedRange
                || retainedCount != lastRetainedCount
                || (now - lastCoverageNanos) >= 250_000_000L;
        if (dirty) {
            if (coverage == null || coverageSize != size) {
                coverage = BufferUtils.createByteBuffer(size * size * 2);
                coverageSize = size;
            }
            for (int row = 0; row < size; row++) {
                writeCoverageRow(game.level, row * size * 2, player.x, player.z, row - range, playerRange);
                writeCoverageRow(game.level, row * size * 2 + size, cx, cz, row - range, retainedRange);
            }
            lastPx = player.x; lastPz = player.z;
            lastCx = cx; lastCz = cz;
            lastPlayerRange = playerRange; lastRetainedRange = retainedRange;
            lastRetainedCount = retainedCount;
            lastCoverageNanos = now;
        }

        // Use raw GL calls consistently: DH mutates GL state directly, so GlStateManager's cache may be stale.
        int oldDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean oldDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean oldDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int oldUnpack = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int oldRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int oldSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int oldSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int oldLod = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int oldCoverage = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer colorMask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            IntBuffer oldViewport = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, oldViewport);
            try {
                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, coverageTexture);
                if (dirty || allocatedSize != size) {
                    uploadCoverage(size);
                }
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depth);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
                GL11.glViewport(0, 0, target.viewWidth, target.viewHeight);
                GL11.glColorMask(false, false, false, false);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL20.glUseProgram(program);
                GL20.glUniform2f(lodZ, m22.getFloat(dh), m23.getFloat(dh));
                GL20.glUniform2f(worldZ, m22.getFloat(mc), m23.getFloat(mc));
                GL20.glUniform2f(focalScale, m00.getFloat(dh), m11.getFloat(dh));
                GL20.glUniformMatrix3fv(viewToWorld, false, TMP_MAT3.set(camera.rotation()).get(stack.mallocFloat(9)));
                GL20.glUniform2i(cameraChunk, cx, cz);
                GL20.glUniform2f(cameraOffset, (float) (pos.x - cx * 16.0), (float) (pos.z - cz * 16.0));
                GL20.glUniform2i(playerOrigin, player.x - range, player.z - range);
                GL20.glUniform2i(retainedOrigin, cx - range, cz - range);
                GL20.glUniform1i(sizeLocation, size);
                GL30.glBindVertexArray(vao);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            } finally {
                GL30.glBindVertexArray(oldVao);
                GL20.glUseProgram(oldProgram);
                GL11.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                        colorMask.get(2) != 0, colorMask.get(3) != 0);
                GL11.glDepthMask(oldDepthMask);
                GL11.glDepthFunc(oldDepthFunc);
                if (!oldDepthTest) GL11.glDisable(GL11.GL_DEPTH_TEST);
                else GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glViewport(oldViewport.get(0), oldViewport.get(1), oldViewport.get(2), oldViewport.get(3));
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDrawFbo);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, oldUnpack);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, oldRowLength);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, oldSkipRows);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, oldSkipPixels);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldLod);
                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldCoverage);
                GL13.glActiveTexture(oldActive);
            }
        }
    }

    private static void writeCoverageRow(ClientLevel level, int index, int cx, int cz, int dz, int radius) {
        for (int x = 0; x < coverageSize; x++) {
            int dx = x - coverageSize / 2;
            boolean covered = radius > 0 && Math.abs(dx) <= radius && Math.abs(dz) <= radius
                    && PipLevelRenderer.isSurfaceReady(level, cx + dx, cz + dz);
            coverage.put(index + x, (byte) (covered ? 255 : 0));
        }
    }

    private static void uploadCoverage(int size) {
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        if (allocatedSize != size) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, size * 2, size, 0,
                    GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, coverage);
            allocatedSize = size;
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, size * 2, size,
                    GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, coverage);
        }
    }

    private static void init(Class<?> params) throws ReflectiveOperationException {
        dhProjection = params.getField("dhProjectionMatrix");
        mcProjection = params.getField("mcProjectionMatrix");
        Class<?> matrix = dhProjection.getType();
        m00 = matrix.getField("m00");
        m11 = matrix.getField("m11");
        m22 = matrix.getField("m22");
        m23 = matrix.getField("m23");

        program = createProgram(FSH);
        vao = GL30.glGenVertexArrays();
        coverageTexture = GL11.glGenTextures();
        lodZ = GL20.glGetUniformLocation(program, "LodProjectionZ");
        worldZ = GL20.glGetUniformLocation(program, "WorldProjectionZ");
        focalScale = GL20.glGetUniformLocation(program, "FocalScale");
        viewToWorld = GL20.glGetUniformLocation(program, "ViewToWorld");
        cameraChunk = GL20.glGetUniformLocation(program, "CameraChunk");
        cameraOffset = GL20.glGetUniformLocation(program, "CameraOffset");
        playerOrigin = GL20.glGetUniformLocation(program, "PlayerOrigin");
        retainedOrigin = GL20.glGetUniformLocation(program, "RetainedOrigin");
        sizeLocation = GL20.glGetUniformLocation(program, "CoverageSize");
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "LodDepth"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "Coverage"), 1);
        GL20.glUseProgram(oldProgram);
    }

    private static int createProgram(String fragment) {
        int vs = 0, fs = 0, p = 0;
        try {
            vs = compileShader(GL20.GL_VERTEX_SHADER, VSH);
            fs = compileShader(GL20.GL_FRAGMENT_SHADER, fragment);
            p = GL20.glCreateProgram();
            GL20.glAttachShader(p, vs);
            GL20.glAttachShader(p, fs);
            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException("link: " + GL20.glGetProgramInfoLog(p));
            }
            return p;
        } catch (RuntimeException e) {
            if (p != 0) GL20.glDeleteProgram(p);
            throw e;
        } finally {
            if (vs != 0) GL20.glDeleteShader(vs);
            if (fs != 0) GL20.glDeleteShader(fs);
        }
    }

    private static int compileShader(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(s);
            GL20.glDeleteShader(s);
            throw new IllegalStateException("compile: " + log);
        }
        return s;
    }
}
