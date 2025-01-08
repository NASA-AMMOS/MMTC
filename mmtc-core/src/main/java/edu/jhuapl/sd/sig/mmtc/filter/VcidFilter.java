package edu.jhuapl.sd.sig.mmtc.filter;

import java.util.*;
import java.util.stream.Collectors;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.util.CollectionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;

public class VcidFilter implements TimeCorrelationFilter {
	private static final Logger logger = LogManager.getLogger();

	/**
	 * Check that the VCIDs (and TK VCIDs, if populated) of a set of samples are all from a single set of VCIDs (for example, all long frames, or all short frames.)
	 *
	 * Simple usages of this filter are also possible, e.g.:
	 * - a single group of a single VCID, to ensure all frames in the sample set are of that VCID
	 * - multiple groups of a single different VCID each, to ensure all frames in the sample set are of the same expected VCID
	 *
	 * @param samples the sample set
	 * @param config  the app configuration
	 * @return true if all samples have valid VCIDs (and TK VCIDs) according to the given configuration; false otherwise
	 */
	@Override
	public boolean process(List<FrameSample> samples, TimeCorrelationAppConfig config) throws MmtcException {
		if (samples.isEmpty()) {
			logger.warn("VCID Filter failed: Attempted to filter an empty sample set");
			return false;
		}

		final Collection<Set<Integer>> allowableVcidGroups = config.getVcidFilterValidVcidGroups();
		final Set<Integer> seenVcids = new HashSet<>();

		for (FrameSample sample : samples) {
			seenVcids.add(sample.getVcid());

			if (sample.isTkVcidSet()) {
				seenVcids.add(sample.getTkVcid());
			}

			if (! CollectionUtil.setsContainIntersectingSet(allowableVcidGroups, seenVcids)) {
				logger.warn("VCID Filter failed: Sample with ERT " + sample.getErtStr() + " has VCID " + sample.getVcid()
						+ (sample.isTkVcidSet() ? " and TK VCID " + sample.getTkVcid() : "") +
						", and the VCIDs checked in this sample set so far no longer match any group of allowable group of VCIDs." +
						"VCIDs (and TK VCIDs, if set) within this sample set thus far: " + CollectionUtil.prettyPrint(seenVcids) + "\n" +
						"Allowable groups of VCIDs: " + allowableVcidGroups.stream().map(CollectionUtil::prettyPrint).collect(Collectors.joining("; ")));

				return false;
			}
		}
		return true;
	}
}
