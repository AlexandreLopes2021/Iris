package net.irisshaders.iris.mixin.vertices;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferVertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.irisshaders.iris.block_rendering.BlockRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder;
import net.irisshaders.iris.vertices.BufferBuilderPolygonView;
import net.irisshaders.iris.vertices.ExtendedDataHelper;
import net.irisshaders.iris.vertices.ExtendingBufferBuilder;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.irisshaders.iris.vertices.NormalHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/**
 * Dynamically and transparently extends the vanilla vertex formats with additional data
 */
@Mixin(BufferBuilder.class)
public abstract class MixinBufferBuilder extends DefaultedVertexConsumer implements BufferVertexConsumer, BlockSensitiveBufferBuilder, ExtendingBufferBuilder {
	@Unique
	private final BufferBuilderPolygonView polygon = new BufferBuilderPolygonView();
	@Unique
	private final Vector3f normal = new Vector3f();
	@Unique
	private boolean extending;
	@Unique
	private boolean iris$shouldNotExtend = false;
	@Unique
	private boolean iris$isTerrain = false;
	@Unique
	private int vertexCount;
	@Unique
	private boolean injectNormal;

	@Unique
	private short currentBlock;

	@Unique
	private short currentRenderType;

	@Unique
	private int currentLocalPosX;

	@Unique
	private int currentLocalPosY;

	@Unique
	private int currentLocalPosZ;

	@Shadow
	private boolean fastFormat;

	@Shadow
	private boolean fullFormat;

	@Shadow
	private ByteBuffer buffer;

	@Shadow
	private VertexFormat.Mode mode;

	@Shadow
	private VertexFormat format;

	@Shadow
	private int nextElementByte;

	@Shadow
	private @Nullable VertexFormatElement currentElement;
	private float midU;
	private float midV;

	@Shadow
	public abstract void begin(VertexFormat.Mode drawMode, VertexFormat vertexFormat);

	@Shadow
	public abstract void putShort(int i, short s);

	@Shadow
	protected abstract void switchFormat(VertexFormat arg);

	@Override
	public void iris$beginWithoutExtending(VertexFormat.Mode drawMode, VertexFormat vertexFormat) {
		iris$shouldNotExtend = true;
		begin(drawMode, vertexFormat);
		iris$shouldNotExtend = false;
	}

	@Inject(method = "begin", at = @At("HEAD"))
	private void iris$onBegin(VertexFormat.Mode drawMode, VertexFormat format, CallbackInfo ci) {
		boolean shouldExtend = (!iris$shouldNotExtend) && BlockRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat();
		extending = shouldExtend && (format == DefaultVertexFormat.BLOCK || format == DefaultVertexFormat.NEW_ENTITY
			|| format == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
		vertexCount = 0;

		if (extending) {
			injectNormal = format == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP;
		}
	}

	@Inject(method = "begin", at = @At("RETURN"))
	private void iris$afterBegin(VertexFormat.Mode drawMode, VertexFormat format, CallbackInfo ci) {
		if (extending) {
			if (format == DefaultVertexFormat.NEW_ENTITY) {
				this.switchFormat(IrisVertexFormats.ENTITY);
				this.iris$isTerrain = false;
			} else {
				this.switchFormat(IrisVertexFormats.TERRAIN);
				this.iris$isTerrain = true;
			}
			this.currentElement = this.format.getElements().get(0);
		}
	}

	@ModifyArg(method = "begin", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;switchFormat(Lcom/mojang/blaze3d/vertex/VertexFormat;)V"))
	private VertexFormat iris$afterBeginSwitchFormat(VertexFormat arg) {
		if (extending) {
			if (format == DefaultVertexFormat.NEW_ENTITY) {
				return IrisVertexFormats.ENTITY;
			} else {
				return IrisVertexFormats.TERRAIN;
			}
		}
		return arg;
	}

	@Override
	public VertexConsumer uv(float u, float v) {
		midU += u;
		midV += v;
		if (extending && !iris$isTerrain) {
			this.putShort(0, encodeBlockTexture(u));
			this.putShort(2, encodeBlockTexture(v));
			this.nextElement();
			return this;
		}
		return BufferVertexConsumer.super.uv(u, v);
	}

	@Inject(method = "discard()V", at = @At("HEAD"))
	private void iris$onDiscard(CallbackInfo ci) {
		extending = false;
		vertexCount = 0;
	}

	@Inject(method = "switchFormat", at = @At("RETURN"))
	private void iris$preventHardcodedVertexWriting(VertexFormat format, CallbackInfo ci) {
		if (!extending) {
			return;
		}

		fastFormat = false;
		fullFormat = false;
	}

	@Inject(method = "endVertex", at = @At("HEAD"))
	private void iris$beforeNext(CallbackInfo ci) {
		if (!extending) {
			return;
		}

		if (injectNormal && currentElement == DefaultVertexFormat.ELEMENT_NORMAL) {
			this.putInt(0, 0);
			this.nextElement();
		}

		if (iris$isTerrain) {
			// ENTITY_ELEMENT
			this.putShort(0, currentBlock);
			this.putShort(2, currentRenderType);
		} else {
			// ENTITY_ELEMENT
			this.putShort(0, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedEntity());
			this.putShort(2, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());
			this.putShort(4, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedItem());
		}

		this.nextElement();

		// MID_TEXTURE_ELEMENT
		if (iris$isTerrain) {
			this.putFloat(0, 0);
			this.putFloat(4, 0);
		} else {
			this.putShort(0, (short) 0);
			this.putShort(2, (short) 0);
		}
		this.nextElement();
		// TANGENT_ELEMENT
		this.putInt(0, 0);
		this.nextElement();
		if (iris$isTerrain) {
			// MID_BLOCK_ELEMENT
			int posIndex = this.nextElementByte - 48;
			float x = buffer.getFloat(posIndex);
			float y = buffer.getFloat(posIndex + 4);
			float z = buffer.getFloat(posIndex + 8);
			this.putInt(0, ExtendedDataHelper.computeMidBlock(x, y, z, currentLocalPosX, currentLocalPosY, currentLocalPosZ));
			this.nextElement();
		}

		vertexCount++;

		if (mode == VertexFormat.Mode.QUADS && vertexCount == 4 || mode == VertexFormat.Mode.TRIANGLES && vertexCount == 3) {
			fillExtendedData(vertexCount);
		}
	}

	@Unique
	private static short encodeBlockTexture(float value) {
		return (short) (Math.min(0.99999997F, value) * 65536);
	}

	@Unique
	private void fillExtendedData(int vertexAmount) {
		vertexCount = 0;

		int stride = format.getVertexSize();

		polygon.setup(buffer, nextElementByte, stride, vertexAmount, !iris$isTerrain);

		midU /= vertexAmount;
		midV /= vertexAmount;

		if (vertexAmount == 3) {
			NormalHelper.computeFaceNormalTri(normal, polygon);
		} else {
			NormalHelper.computeFaceNormal(normal, polygon);
		}

		int packedNormal = NormalHelper.packNormal(normal, 0.0f);

		int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, polygon);

		int midUOffset;
		int midVOffset;
		int normalOffset;
		int tangentOffset;

		if (!iris$isTerrain) {
			//System.out.println(midU + " " + midV);
		}
		if (iris$isTerrain) {
			midUOffset = 16;
			midVOffset = 12;
			normalOffset = 24;
			tangentOffset = 8;
		} else {
			midUOffset = 8;
			midVOffset = 6;
			normalOffset = 18;
			tangentOffset = 4;
		}

		for (int vertex = 0; vertex < vertexAmount; vertex++) {
			if (iris$isTerrain) {
				buffer.putFloat(nextElementByte - midUOffset - stride * vertex, midU);
				buffer.putFloat(nextElementByte - midVOffset - stride * vertex, midV);
			} else {
				buffer.putShort(nextElementByte - midUOffset - stride * vertex, encodeBlockTexture(midU));
				buffer.putShort(nextElementByte - midVOffset - stride * vertex, encodeBlockTexture(midV));
			}
			buffer.putInt(nextElementByte - normalOffset - stride * vertex, packedNormal);
			buffer.putInt(nextElementByte - tangentOffset - stride * vertex, tangent);
		}

		midU = 0f;
		midV = 0f;
	}

	@Override
	public void beginBlock(short block, short renderType, int localPosX, int localPosY, int localPosZ) {
		this.currentBlock = block;
		this.currentRenderType = renderType;
		this.currentLocalPosX = localPosX;
		this.currentLocalPosY = localPosY;
		this.currentLocalPosZ = localPosZ;
	}

	@Override
	public void endBlock() {
		this.currentBlock = -1;
		this.currentRenderType = -1;
		this.currentLocalPosX = 0;
		this.currentLocalPosY = 0;
		this.currentLocalPosZ = 0;
	}

	@Unique
	private void putInt(int i, int value) {
		this.buffer.putInt(this.nextElementByte + i, value);
	}
}
