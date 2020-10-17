package players.mctsag;

import players.optimisers.ParameterSet;
import utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class MCTSAGParams implements ParameterSet {

    // Constants
    public final double HUGE_NEGATIVE = -1000;
    public final double HUGE_POSITIVE =  1000;

    public final int STOP_TIME = 0;
    public final int STOP_ITERATIONS = 1;
    public final int STOP_FMCALLS = 2;

    public final int CUSTOM_HEURISTIC = 0;
    public final int ADVANCED_HEURISTIC = 1;

    public double epsilon = 1e-6;

    // Parameters
    public double K = 1;
    public int rollout_depth = 18;//10;
    public int heuristic_method = ADVANCED_HEURISTIC;
    public double decay_factor = 0.78; // Must be less than 1

    // Budget settings
    public int stop_type = STOP_TIME;
    public int num_iterations = 300;
    public int num_fmcalls = 2000;
    public int num_time = 40;

    // Opponent Modelling
    public enum OppModel {
        RANDOM(),
        MIRROR(),
        SAME_ACTION()
    }

    // Random Model is the default
    public OppModel opp_model = OppModel.RANDOM;


    @Override
    public void setParameterValue(String param, Object value) {
        switch(param) {
            case "K": K = (double) value; break;
            case "rollout_depth": rollout_depth = (int) value; break;
            case "num_iterations": num_iterations = (int) value; break;
            case "decay_factor": decay_factor = (double) value; break;
            case "heuristic_method": heuristic_method = (int) value; break;
        }
    }

    @Override
    public Object getParameterValue(String param) {
        switch(param) {
            case "K": return K;
            case "rollout_depth": return rollout_depth;
            case "num_iterations": return num_iterations;
            case "decay_factor": return decay_factor;
            case "heuristic_method": return heuristic_method;
        }
        return null;
    }

    @Override
    public ArrayList<String> getParameters() {
        ArrayList<String> paramList = new ArrayList<>();
        paramList.add("K");
        paramList.add("rollout_depth");
        paramList.add("num_iterations");
        paramList.add("decay_factor");
        paramList.add("heuristic_method");
        return paramList;
    }

    @Override
    public Map<String, Object[]> getParameterValues() {
        HashMap<String, Object[]> parameterValues = new HashMap<>();
        parameterValues.put("K", new Double[]{1.0, Math.sqrt(2), 2.0});
        parameterValues.put("rollout_depth", new Integer[]{10, 12, 15, 16, 17, 18});
        parameterValues.put("num_iterations", new Integer[]{240, 260, 280, 300, 310});
        parameterValues.put("decay_factor", new Double[] {0.85, 0.9, 0.92, 0.94, 0.96, 0.98});
        parameterValues.put("heuristic_method", new Integer[]{CUSTOM_HEURISTIC, ADVANCED_HEURISTIC});
        return parameterValues;
    }

    @Override
    public Pair<String, ArrayList<Object>> getParameterParent(String parameter) {
        return null;  // No parameter dependencies
    }

    @Override
    public Map<Object, ArrayList<String>> getParameterChildren(String root) {
        return new HashMap<>();  // No parameter dependencies
    }

    @Override
    public Map<String, String[]> constantNames() {
        HashMap<String, String[]> names = new HashMap<>();
        names.put("heuristic_method", new String[]{"CUSTOM_HEURISTIC", "ADVANCED_HEURISTIC"});
        return names;
    }
}
