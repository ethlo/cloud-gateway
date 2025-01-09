package com.ethlo.http.logger;

import java.util.List;

public class RedactUtil
{
    public static List<String> redactAll(List<String> headerValues)
    {
        return headerValues.stream().map(RedactUtil::redact).toList();
    }

    public static String redact(final String input, int include)
    {
        if (input == null)
        {
            return null;
        }
        else if (include < 0 || input.length() <= include + 6)
        {
            return "*****";
        }
        return input.substring(0, include) + "*****" + input.substring(input.length() - include);
    }

    public static String redact(String input)
    {
        return redact(input, 1);
    }
}
