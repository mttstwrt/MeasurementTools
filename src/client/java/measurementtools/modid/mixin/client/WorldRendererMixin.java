package measurementtools.modid.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import measurementtools.modid.render.MeasurementRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderEnd(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
                             Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f matrix3,
                             GpuBufferSlice bufferSlice, Vector4f vector, boolean flag,
                             CallbackInfo ci) {
        MeasurementRenderer.getInstance().render(camera, positionMatrix);
    }
}
