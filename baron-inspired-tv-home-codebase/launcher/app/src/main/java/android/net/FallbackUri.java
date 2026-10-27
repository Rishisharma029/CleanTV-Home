package android.net;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.List;

/**
 * Lightweight Uri implementation for JVM unit tests and Android runtime.
 */
public class FallbackUri extends Uri {
    private final String uriString;

    public FallbackUri(String uriString) {
        this.uriString = uriString != null ? uriString : "";
    }

    @Override
    public boolean isHierarchical() {
        return true;
    }

    @Override
    public boolean isRelative() {
        return false;
    }

    @Override
    public String getScheme() {
        int idx = uriString.indexOf(":");
        return idx > 0 ? uriString.substring(0, idx) : null;
    }

    @Override
    public String getSchemeSpecificPart() {
        int idx = uriString.indexOf(":");
        return idx > 0 ? uriString.substring(idx + 1) : uriString;
    }

    @Override
    public String getEncodedSchemeSpecificPart() {
        return getSchemeSpecificPart();
    }

    @Override
    public String getAuthority() {
        return null;
    }

    @Override
    public String getEncodedAuthority() {
        return null;
    }

    @Override
    public String getUserInfo() {
        return null;
    }

    @Override
    public String getEncodedUserInfo() {
        return null;
    }

    @Override
    public String getHost() {
        return null;
    }

    @Override
    public int getPort() {
        return -1;
    }

    @Override
    public String getPath() {
        return null;
    }

    @Override
    public String getEncodedPath() {
        return null;
    }

    @Override
    public String getQuery() {
        int idx = uriString.indexOf("?");
        return idx >= 0 ? uriString.substring(idx + 1) : null;
    }

    @Override
    public String getEncodedQuery() {
        return getQuery();
    }

    @Override
    public String getFragment() {
        return null;
    }

    @Override
    public String getEncodedFragment() {
        return null;
    }

    @Override
    public List<String> getPathSegments() {
        return Collections.emptyList();
    }

    @Override
    public String getLastPathSegment() {
        return null;
    }

    @Override
    public String toString() {
        return uriString;
    }

    @Override
    public Builder buildUpon() {
        return new Builder();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (dest != null) {
            try {
                dest.writeString(uriString);
            } catch (Throwable ignored) {}
        }
    }

    public static final Parcelable.Creator<FallbackUri> CREATOR = new Parcelable.Creator<FallbackUri>() {
        @Override
        public FallbackUri createFromParcel(Parcel in) {
            String str = "";
            if (in != null) {
                try {
                    str = in.readString();
                } catch (Throwable ignored) {}
            }
            return new FallbackUri(str != null ? str : "");
        }

        @Override
        public FallbackUri[] newArray(int size) {
            return new FallbackUri[size];
        }
    };
}
