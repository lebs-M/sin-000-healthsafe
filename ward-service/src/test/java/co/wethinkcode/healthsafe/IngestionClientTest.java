package co.wethinkcode.healthsafe;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngestionClientTest {

    private HttpServer server;

    /** Starts a stub ingestion-service on an ephemeral port and returns its base URL. */
    private String stubIngestion(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/wards", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesTheWardListAndIgnoresFieldsItDoesNotModel() throws Exception {
        String body = """
                [{"wardId":"W-05","wing":"East Wing","department":"Paediatrics","bedsAvailable":5,
                  "notes":["merged duplicate row 6"],"attributes":{"floor":"2"},"sourceRows":[5,6]}]""";
        IngestionClient client = new IngestionClient(stubIngestion(200, body));

        List<Ward> wards = client.fetchWards();

        assertEquals(1, wards.size());
        Ward ward = wards.get(0);
        assertEquals("W-05", ward.wardId());
        assertEquals("East Wing", ward.wing());
        assertEquals("Paediatrics", ward.department());
        assertEquals(5, ward.bedsAvailable());
        assertEquals(List.of("merged duplicate row 6"), ward.notes());
    }

    @Test
    void toleratesBlankAndNullBedsValues() throws Exception {
        String body = "[{\"wardId\":\"W-02\",\"wing\":\"West Wing\",\"department\":null,"
                + "\"bedsAvailable\":null,\"notes\":[]}]";
        IngestionClient client = new IngestionClient(stubIngestion(200, body));

        assertNull(client.fetchWards().get(0).bedsAvailable());
    }

    @Test
    void nonOkStatusIsSurfacedAsAnIOException() throws Exception {
        IngestionClient client = new IngestionClient(stubIngestion(500, "boom"));

        assertThrows(IOException.class, client::fetchWards);
    }
}
