package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.LivingEntityStickExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    // PORT-NOTE(mc26.1): LevelRenderer.renderLineBox was removed; ShapeRenderer.renderShape over
    // Shapes.create(AABB) draws the same 12 box edges.
    @Unique
    private static void sable$renderLineBox(final PoseStack poseStack, final VertexConsumer consumer, final AABB box, final float r, final float g, final float b, final float a) {
        ShapeRenderer.renderShape(poseStack, consumer, Shapes.create(box), 0.0, 0.0, 0.0, ARGB.colorFromFloat(a, r, g, b), 1.0F);
    }

    // PORT-NOTE(mc26.1): EntityRenderDispatcher.renderHitbox no longer exists (hitbox debug rendering
    // moved into the render-state pipeline). require = 0 keeps the game bootable; re-target when the
    // render cluster is ported.
    @Inject(method = "renderHitbox", at = @At("TAIL"), require = 0)
    private static void renderHitbox(final PoseStack poseStack, final VertexConsumer vertexConsumer, final Entity entity, final float partialTicks, final float g, final float h, final float i, final CallbackInfo ci) {
        // collision hitbox
        final SubLevel tracking = Sable.HELPER.getTrackingSubLevel(entity);

        if (tracking instanceof final ClientSubLevel clientSubLevel) {
            Quaterniondc customOrientation = EntitySubLevelUtil.getCustomEntityOrientation(entity, partialTicks);
            if (customOrientation == null)
                customOrientation = JOMLConversion.QUAT_IDENTITY;

            final double yaw = SubLevelEntityCollision.getHitBoxYaw(clientSubLevel.renderPose());

            poseStack.pushPose();
            final AABB bounds = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());
            poseStack.translate(0.0, entity.getEyeHeight(), 0.0);
            poseStack.mulPose(new Quaternionf(customOrientation).rotateY((float) yaw));
            poseStack.translate(0.0, -entity.getEyeHeight(), 0.0);
            sable$renderLineBox(poseStack, vertexConsumer, bounds, 1.0F, 1.0F, 0.0F, 0.4F);
            poseStack.popPose();
        }

        final EntityStickExtension duck = (EntityStickExtension) entity;
        final Vec3 plotPosition = duck.sable$getPlotPosition();
        if (plotPosition != null) {
            final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(entity.level(), plotPosition);
            if (subLevel != null) {
                final Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
                final Vec3 projectedPos = subLevel.renderPose().transformPosition(plotPosition);

                poseStack.popPose();
                final AABB aABB = entity.getType().getSpawnAABB(projectedPos.x - cam.x, projectedPos.y - cam.y, projectedPos.z - cam.z);
                sable$renderLineBox(poseStack, vertexConsumer, aABB, 0.0F, 1.0F, 0.0F, 0.2F);

                if (entity instanceof final LivingEntityStickExtension livingDuck) {
                    final Vec3 serverProjectedPos = subLevel.renderPose().transformPosition(livingDuck.sable$getLerpTarget());
                    final AABB aABB3 = entity.getType().getSpawnAABB(serverProjectedPos.x - cam.x, serverProjectedPos.y - cam.y, serverProjectedPos.z - cam.z);
                    sable$renderLineBox(poseStack, vertexConsumer, aABB3, 1.0F, 0.0F, 1.0F, 0.2F);
                }

                poseStack.pushPose();
            }
        }
    }
}
