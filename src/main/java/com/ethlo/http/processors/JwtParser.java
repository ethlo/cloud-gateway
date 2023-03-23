package com.ethlo.http.processors;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;

public class JwtParser
{
    public static String getUsername(final String jwtToken)
    {
        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime() // the JWT must have an expiration time
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setJwsAlgorithmConstraints( // only allow the expected signature algorithm(s) in the given context
                        AlgorithmConstraints.ConstraintType.PERMIT, AlgorithmIdentifiers.RSA_USING_SHA256) // which is only RS256 here
                .build(); // create the JwtConsumer instance
        try
        {
            return jwtConsumer.process(jwtToken).getJwtClaims().getClaimValueAsString("sub");
        }
        catch (InvalidJwtException e)
        {
            throw new UncheckedIOException(new IOException(e));
        }
    }
}
