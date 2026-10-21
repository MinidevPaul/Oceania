package com.oceania.mob;

import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

public enum SharkVariant implements StringRepresentable {

	REEF("reef", Shape.slender(5.2F, 4.5F, 8.0F, 9.4F, 5.1F, 9.0F, 4.9F, 6.1F, 6.5F),
		20.0, 9.0, 1.15, 0.90F,
		new int[]{0, 0, 30, 0, 0, 19, 58, 0, 78, 19, 102, 19, 22, 19, 86, 0, 50, 19, 108, 0, 64, 19, 92, 19, 112, 19, 16, 32, 0, 32, 32, 32}),
	GREAT_WHITE("great_white", Shape.stocky(6.4F, 4.5F, 9.0F, 8.7F, 6.4F, 10.0F, 5.2F, 6.5F, 7.5F),
		32.0, 12.0, 1.05, 2.60F,
		new int[]{0, 0, 72, 0, 0, 22, 36, 0, 104, 22, 10, 36, 24, 22, 104, 0, 74, 22, 58, 22, 90, 22, 0, 36, 22, 36, 46, 36, 30, 36, 66, 36}),
	TIGER("tiger", Shape.stocky(5.8F, 4.0F, 8.0F, 10.7F, 4.2F, 9.0F, 4.4F, 5.5F, 6.5F),
		26.0, 11.0, 1.10, 1.10F,
		new int[]{0, 0, 34, 0, 0, 21, 66, 0, 98, 21, 0, 35, 24, 21, 98, 0, 54, 21, 68, 21, 84, 21, 112, 21, 120, 0, 28, 35, 12, 35, 48, 35}),
	BULL("bull", Shape.stocky(6.6F, 3.5F, 8.0F, 8.5F, 4.2F, 8.0F, 4.6F, 5.8F, 5.5F),
		26.0, 11.0, 1.20, 1.00F,
		new int[]{0, 0, 36, 0, 104, 0, 70, 0, 94, 22, 0, 36, 20, 22, 0, 22, 52, 22, 66, 22, 80, 22, 110, 22, 120, 22, 10, 36, 32, 36, 44, 36});

	public static final int TEX_W = 128;
	public static final int TEX_H = 64;

	public enum Box {
		BODY1, BODY2, PEDUNCLE, HEAD, SNOUT, SNOUT_TIP, JAW,
		CAUDAL, CAUDAL_TIP, CAUDAL_LOWER, DORSAL, DORSAL_TIP, DORSAL2,
		PELVIC, PECTORAL, PECTORAL_TIP
	}

	public static final SharkVariant[] BY_ID = values();

	private final String name;
	private final Shape shape;
	private final double health;
	private final double damage;
	private final double speed;
	private final float scale;
	private final int[] uv;
	private final Identifier texture;

	SharkVariant(String name, Shape shape, double health, double damage, double speed,
	             float scale, int[] uv) {
		this.name = name;
		this.shape = shape;
		this.health = health;
		this.damage = damage;
		this.speed = speed;
		this.scale = scale;
		this.uv = uv;
		this.texture = Identifier.fromNamespaceAndPath("oceania", "textures/entity/shark/" + name + ".png");
	}

	public int texU(Box box) {
		return this.uv[box.ordinal() * 2];
	}

	public int texV(Box box) {
		return this.uv[box.ordinal() * 2 + 1];
	}

	public static SharkVariant byId(int id) {
		return BY_ID[Math.floorMod(id, BY_ID.length)];
	}

	public Shape shape() {
		return this.shape;
	}

	public double health() {
		return this.health;
	}

	public double damage() {
		return this.damage;
	}

	public double speed() {
		return this.speed;
	}

	public float scale() {
		return this.scale;
	}

	public Identifier texture() {
		return this.texture;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public record Shape(float girth, float snoutLen, float headLen,
	                    float caudalUp, float caudalLo, float caudalLen,
	                    float dorsalH, float dorsalLen, float pectLen,
	                    float widthRatio) {

		static Shape slender(float girth, float snoutLen, float headLen, float caudalUp,
		                     float caudalLo, float caudalLen, float dorsalH, float dorsalLen,
		                     float pectLen) {
			return new Shape(girth, snoutLen, headLen, caudalUp, caudalLo, caudalLen,
				dorsalH, dorsalLen, pectLen, 1.20F);
		}

		static Shape stocky(float girth, float snoutLen, float headLen, float caudalUp,
		                    float caudalLo, float caudalLen, float dorsalH, float dorsalLen,
		                    float pectLen) {
			return new Shape(girth, snoutLen, headLen, caudalUp, caudalLo, caudalLen,
				dorsalH, dorsalLen, pectLen, 1.38F);
		}
	}
}
