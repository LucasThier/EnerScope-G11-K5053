package org.enerscope.economic.dto;
public record EconomicJobRequest(Integer rounds, String seed) {
    public int roundCount() {
        int count=rounds==null?10:rounds;
        if(count<1 || count>100) throw new IllegalArgumentException("Round count must be between 1 and 100");
        return count;
    }
    public long seedValue() {
        if(seed==null || seed.isBlank()) return new java.security.SecureRandom().nextLong();
        try { return Long.parseLong(seed); }
        catch(NumberFormatException error) { throw new IllegalArgumentException("Seed must be a signed 64-bit integer string"); }
    }
}
