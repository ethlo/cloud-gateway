package com.ethlo.http.netty;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;

class TagRequestIdGlobalFilterStatusCodeTest
{
    @Test
    void theResponseStatusIsUsedWhenThereIsNoException()
    {
        final MockServerHttpResponse response = new MockServerHttpResponse();
        response.setStatusCode(HttpStatus.NO_CONTENT);

        assertThat(TagRequestIdGlobalFilter.determineStatusCode(null, response)).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void anUncommittedResponseTakesItsStatusFromAResponseStatusException()
    {
        final MockServerHttpResponse response = new MockServerHttpResponse();

        assertThat(TagRequestIdGlobalFilter.determineStatusCode(new ResponseStatusException(HttpStatus.BAD_GATEWAY), response))
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void anUncommittedResponseFallsBackToServerErrorForAnyOtherException()
    {
        final MockServerHttpResponse response = new MockServerHttpResponse();
        response.setStatusCode(HttpStatus.OK);

        assertThat(TagRequestIdGlobalFilter.determineStatusCode(new IOException("upstream went away"), response))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * An upstream closing the connection mid-response cannot retract the status line the client already received,
     * so logging a synthetic 500 would contradict what actually happened on the wire.
     */
    @Test
    void aCommittedResponseKeepsTheStatusTheClientReceived()
    {
        final MockServerHttpResponse response = new MockServerHttpResponse();
        response.setStatusCode(HttpStatus.OK);
        response.setComplete().block();
        assertThat(response.isCommitted()).isTrue();

        assertThat(TagRequestIdGlobalFilter.determineStatusCode(new IOException("Connection prematurely closed DURING response"), response))
                .isEqualTo(HttpStatus.OK);
    }
}
