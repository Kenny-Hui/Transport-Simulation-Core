package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

public abstract class AreaBase<T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> extends NameColorDataBase {

	public long cornerXMin;
	public long cornerZMin;
	public long cornerXMax;
	public long cornerZMax;
	public final ObjectAVLTreeSet<U> savedRails = new ObjectAVLTreeSet<>();

	private static final String KEY_X_MIN = "x_min";
	private static final String KEY_Z_MIN = "z_min";
	private static final String KEY_X_MAX = "x_max";
	private static final String KEY_Z_MAX = "z_max";

	public AreaBase(long id) {
		super(id);
	}

	public AreaBase(long id, TransportMode transportMode) {
		super(id, transportMode);
	}

	public AreaBase(ReaderBase readerBase) {
		super(readerBase);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		super.updateData(readerBase);

		readerBase.unpackLong(KEY_X_MIN, value -> cornerXMin = value);
		readerBase.unpackLong(KEY_Z_MIN, value -> cornerZMin = value);
		readerBase.unpackLong(KEY_X_MAX, value -> cornerXMax = value);
		readerBase.unpackLong(KEY_Z_MAX, value -> cornerZMax = value);

		if (cornerXMax < cornerXMin) {
			long temp = cornerXMax;
			cornerXMax = cornerXMin;
			cornerXMin = temp;
		}
		if (cornerZMax < cornerZMin) {
			long temp = cornerZMax;
			cornerZMax = cornerZMin;
			cornerZMin = temp;
		}
	}

	@Override
	public void toMessagePack(WriterBase writerBase) {
		super.toMessagePack(writerBase);

		writerBase.writeLong(KEY_X_MIN, cornerXMin);
		writerBase.writeLong(KEY_Z_MIN, cornerZMin);
		writerBase.writeLong(KEY_X_MAX, cornerXMax);
		writerBase.writeLong(KEY_Z_MAX, cornerZMax);
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength() + 4;
	}

	public boolean inArea(long x, long z) {
		return validCorners(this) && Utilities.isBetween(x, cornerXMin, cornerXMax) && Utilities.isBetween(z, cornerZMin, cornerZMax);
	}

	public boolean intersecting(AreaBase<T, U> areaBase) {
		return validCorners(this) && validCorners(areaBase) && (inThis(areaBase) || areaBase.inThis(this));
	}

	public Position getCenter() {
		return validCorners(this) ? new Position((cornerXMin + cornerXMax) / 2, 0, (cornerZMin + cornerZMax) / 2) : null;
	}

	private boolean inThis(AreaBase<T, U> areaBase) {
		return inArea(areaBase.cornerXMin, areaBase.cornerZMin) || inArea(areaBase.cornerXMin, areaBase.cornerZMax) || inArea(areaBase.cornerXMax, areaBase.cornerZMin) || inArea(areaBase.cornerXMax, areaBase.cornerZMax);
	}

	public static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> boolean validCorners(AreaBase<T, U> areaBase) {
		return areaBase != null && areaBase.cornerXMax > areaBase.cornerXMin && areaBase.cornerZMax > areaBase.cornerZMin;
	}
}
