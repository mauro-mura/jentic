package dev.jentic.runtime.dialogue.protocol;

import dev.jentic.core.dialogue.Performative;
import dev.jentic.core.dialogue.protocol.Protocol;
import dev.jentic.core.dialogue.protocol.ProtocolState;

import java.util.Set;

import static dev.jentic.core.dialogue.Performative.*;
import static dev.jentic.core.dialogue.protocol.ProtocolState.*;

/**
 * Request protocol for action execution.
 * 
 * <p>Flow:
 * <pre>
 * Initiator                    Participant
 *     |                             |
 *     |-------- REQUEST ----------->|
 *     |                             |
 *     |<------- AGREE/REFUSE -------|
 *     |                             |
 *     |<------- INFORM/FAILURE -----|
 *     |                             |
 * </pre>
 * 
 * @since 0.5.0
 */
public class RequestProtocol implements Protocol {
    
    public static final String ID = "request";
    
    @Override
    public String getId() {
        return ID;
    }
    
    @Override
    public String getDisplayName() {
        return "Request Protocol";
    }
    
    @Override
    public ProtocolState getInitialState() {
        return INITIATED;
    }
    
    @Override
    public ProtocolState nextState(ProtocolState current, Performative received, boolean isInitiator) {
        return switch (current) {
            case INITIATED -> received == REQUEST ? AWAITING_RESPONSE : current;
            case AWAITING_RESPONSE -> switch (received) {
                case AGREE -> AGREED;
                case REFUSE -> REFUSED;
                case INFORM -> COMPLETED;
                case FAILURE -> FAILED;
                default -> current;
            };
            case AGREED -> switch (received) {
                case INFORM -> COMPLETED;
                case FAILURE -> FAILED;
                default -> current;
            };
            default -> current;
        };
    }
    
    @Override
    public Set<Performative> allowedPerformatives(ProtocolState state, boolean isInitiator) {
        if (isInitiator) {
            return switch (state) {
                case INITIATED -> Set.of(REQUEST);
                case AWAITING_RESPONSE, AGREED -> Set.of(CANCEL);
                default -> Set.of();
            };
        } else {
            return switch (state) {
                case AWAITING_RESPONSE -> Set.of(AGREE, REFUSE, INFORM);
                case AGREED -> Set.of(INFORM, FAILURE);
                default -> Set.of();
            };
        }
    }
}