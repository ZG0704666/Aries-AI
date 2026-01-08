package G1;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

/* loaded from: classes.dex */
public abstract class d extends Binder implements e {
    public d() {
        attachInterface(this, "com.ai.assistance.operit.provider.IAccessibilityProvider");
    }

    @Override // android.os.IInterface
    public final IBinder asBinder() {
        return this;
    }

    @Override // G1.e
    public String d() {
        return d();
    }

    @Override // android.os.Binder
    public final boolean onTransact(int i3, Parcel parcel, Parcel parcel2, int i4) {
        if (i3 >= 1 && i3 <= 16777215) {
            parcel.enforceInterface("com.ai.assistance.operit.provider.IAccessibilityProvider");
        }
        if (i3 == 1598968902) {
            parcel2.writeString("com.ai.assistance.operit.provider.IAccessibilityProvider");
            return true;
        }
        switch (i3) {
            case 1:
                String strG = g();
                parcel2.writeNoException();
                parcel2.writeString(strG);
                return true;
            case 2:
                boolean zC = c(parcel.readInt(), parcel.readInt());
                parcel2.writeNoException();
                parcel2.writeInt(zC ? 1 : 0);
                return true;
            case 3:
                boolean zA = a(parcel.readInt(), parcel.readInt());
                parcel2.writeNoException();
                parcel2.writeInt(zA ? 1 : 0);
                return true;
            case 4:
                boolean zB = b(parcel.readInt());
                parcel2.writeNoException();
                parcel2.writeInt(zB ? 1 : 0);
                return true;
            case 5:
                boolean zI = i(parcel.readInt(), parcel.readInt(), parcel.readInt(), parcel.readInt(), parcel.readLong());
                parcel2.writeNoException();
                parcel2.writeInt(zI ? 1 : 0);
                return true;
            case 6:
                String strJ = j();
                parcel2.writeNoException();
                parcel2.writeString(strJ);
                return true;
            case 7:
                boolean zF = f(parcel.readString(), parcel.readString());
                parcel2.writeNoException();
                parcel2.writeInt(zF ? 1 : 0);
                return true;
            case 8:
                boolean zH = h(parcel.readString(), parcel.readString());
                parcel2.writeNoException();
                parcel2.writeInt(zH ? 1 : 0);
                return true;
            case 9:
                boolean zE = e();
                parcel2.writeNoException();
                parcel2.writeInt(zE ? 1 : 0);
                return true;
            case 10:
                String strD = d();
                parcel2.writeNoException();
                parcel2.writeString(strD);
                return true;
            default:
                return super.onTransact(i3, parcel, parcel2, i4);
        }
    }
}
