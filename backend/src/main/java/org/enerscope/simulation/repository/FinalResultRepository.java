package org.enerscope.simulation.repository;

import org.enerscope.simulator.results.FinalResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Persists simulation runs. A run belongs to its version through
 * {@code Version.finalResults}, but it is saved through here: saving the version
 * instead merges it, and a merge leaves the caller holding the transient run —
 * no id, no timestamp — while a managed copy is the one written.
 */
public interface FinalResultRepository extends JpaRepository<FinalResult, UUID> {
}
