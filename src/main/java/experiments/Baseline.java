package experiments;

import data.DataUtils;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;

import java.util.List;

/**
 * Created by Colin Graber on 3/10/16.
 */
public class Baseline {

    public static void main(String [] argv) {
        if (argv.length != 1) {
            System.err.println("Missing arg: directory containing ACE2005 data");
            System.exit(1);
        }

        List<List<TextAnnotation>> splits = DataUtils.loadDataSplits(argv[0]);
    }
}
