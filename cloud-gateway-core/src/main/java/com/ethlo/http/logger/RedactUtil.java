package com.ethlo.http.logger;

import java.util.List;

public class RedactUtil
{
    public static List<String> redactAll(List<String> headerValues)
    {
        return headerValues.stream().map(RedactUtil::redact).toList();
    }

    public static String redact(final String input)
    {
        if (input == null)
        {
            return null;
        }
        else if (input.length() <= 5)
        {
            return "*****";
        }
        return input.charAt(0) + "*****" + input.charAt(input.length() - 1);
    }
}
