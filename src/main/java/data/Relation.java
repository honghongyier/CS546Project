package data;

import utils.Consts;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Colin Graber on 3/18/16.
 */
public class Relation implements Serializable {

    private static final long serialVersionUID = 4L;
    public static List<String> stringList;
    public static Map<String, Integer> labelMap;
    public static Integer labels_count;

    static{


        labelMap = new HashMap<>();
        stringList=new ArrayList<>();

        labelMap.put("NO_RELATION" ,0);
        labelMap.put("GEN-AFF", 1);
        labelMap.put("PER-SOC", 2);
        labelMap.put("ORG-AFF", 3);
        labelMap.put("PHYS", 4);
        labelMap.put("PART-WHOLE", 5);
        labelMap.put("ART" ,6);

        stringList.add("NO_RELATION");
        stringList.add("GEN-AFF");
        stringList.add("PER-SOC");
        stringList.add("ORG-AFF");
        stringList.add("PHYS");
        stringList.add("PART-WHOLE");
        stringList.add("ART");

        labels_count = stringList.size();

    }

    private EntityMention e1;
    private EntityMention e2;


    public String type;
    public int type_num;

    public String pred_type;
    public int pred_num;

    public List<Integer> pred_vector;


    public Relation(String relation, EntityMention e1, EntityMention e2) {
        this.e1 = e1;
        this.e2 = e2;
        this.type = relation;
        this.type_num = labelMap.get(relation);
        pred_vector=null;
    }

    public void SetPrediction(int pred_num){
        this.pred_num = pred_num;
        this.pred_type = stringList.get(pred_num);
    }

    public void SetRelation(int type_num){
        this.type_num = type_num;
        this.type = stringList.get(type_num);
    }


    public EntityMention getArg1() {
        return e1;
    }

    public EntityMention getArg2() {
        return e2;
    }

    public String getType() {
        return type;
    }

    public boolean equals(Relation other) {
        if (!type.equals(other.type)) {
            return false;
        } else if (type.equals(Consts.NO_REL)) {
            // The only symmetric relation is the "No relation" relation
            if (e1.equalsCoarseExtent(other.e1) && e2.equalsCoarseExtent(other.e2)) {
                return true;
            } else if (e1.equalsCoarseExtent(other.e2) && e2.equalsCoarseExtent(other.e1)) {
                return true;
            } else {
                return false;
            }
        } else {
            if (e1.equalsCoarseExtent(other.e1) && e2.equalsCoarseExtent(other.e2)) {
                return true;
            } else {
                return false;
            }
        }
    }
}
