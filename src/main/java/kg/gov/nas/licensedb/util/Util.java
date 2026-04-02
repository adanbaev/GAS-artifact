package kg.gov.nas.licensedb.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Util {
    public static String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    public static String formatDate(Date date, String patter) {
        SimpleDateFormat formatter = new SimpleDateFormat(patter);
        return formatter.format(date);
    }
}
