package lichess.client;

/**
 * Represents an error returned by a Lichess endpoint.
 */
public class LichessServiceException extends Exception {
    private static final long serialVersionUID = 1L;

    public LichessServiceException(String message) {
        super(message);
    }
}
