package data;

import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Sentence;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.reader.ace2005.annotationStructure.*;
import edu.illinois.cs.cogcomp.reader.ace2005.documentReader.AceFileProcessor;
import edu.illinois.cs.cogcomp.reader.util.EventConstants;
import utils.Consts;
import utils.Pipeline;

import java.util.*;

/**
 * This class wraps the information held in ACEDocument and makes it easier to use. ACEDocument contains
 * several pointless layers of wrappers that make doing anything a pain.
 *
 * Created by Colin Graber on 3/11/16.
 */
public class ACEAnnotation {

    // The following two lists hold all of the relation types/subtypes seen
    private static Set<String> relationTypes;
    private static Set<String> entityTypes;
    private static Set<String> entitySubtypes;
    private static Set<String> bioLabels;

    static {
        relationTypes = new HashSet<>();
        relationTypes.add(Consts.NO_REL);
        entityTypes = new HashSet<>();
        entitySubtypes = new HashSet<>();
    }


    private String id;
    private List<TextAnnotation> taList;

    // Each sentence is represented as a List of tokens - this is a list of those lists
    private List<List<String>> sentenceTokens = new ArrayList<>();

    //This list simply contains all of the tokens in a flat list - useful when you don't care about sentence boundaries
    private List<String> tokens = new ArrayList<>();
    private List<EntityMention> goldEntityMentions = new ArrayList<>();
    private Map<String,EntityMention> goldEntityMentionsByID = new HashMap<>();
    private List<EntityMention> testEntityMentions = new ArrayList<>();
    private List<Relation> goldRelations = new ArrayList<>();
    private Map<Pair<EntityMention,EntityMention>,Relation> goldRelationsByArgs = new HashMap<>();
    private List<Relation> testRelations = new ArrayList<>();
    private List<CoreferenceEdge> goldCoreferenceEdges = new ArrayList<>();
    private Map<Pair<EntityMention,EntityMention>,CoreferenceEdge> goldCoreferenceEdgesByEntities = new HashMap<>();
    private List<CoreferenceEdge> testCoreferenceEdges = new ArrayList<>();
    private List<ACERelation> relationList;

    // Annotation info
    private List<List<String>> BIOencoding = null;

    public ACEAnnotation(ACEDocument doc) {
        id = doc.aceAnnotation.id;

        taList = AceFileProcessor.populateTextAnnotation(doc);

        // Since there may be multiple text annotations, each holding multiple sentences, we make accessing sentences
        // easier
        for (TextAnnotation ta: taList) {
            Pipeline.addAllViews(ta);
            for (Sentence sentence: ta.sentences()) {
                sentenceTokens.add(Arrays.asList(sentence.getTokens()));
                tokens.addAll(Arrays.asList(sentence.getTokens()));
            }
        }


        // And now we pull all of the gold data out of the ACEDocumentAnnotation wrapper
        relationList = doc.aceAnnotation.relationList;


        for (ACEEntity entity: doc.aceAnnotation.entityList) {
            System.out.println("MENTION: " + entity.id + ", " + entity.type);
            entityTypes.add(entity.type);
            entitySubtypes.add(entity.subtype);
            List<EntityMention> coreferentEntities = new ArrayList<>();
            for (ACEEntityMention mention: entity.entityMentionList) {
                EntityMention e = makeEntityMention(mention, entity.type);
                goldEntityMentions.add(e);
                coreferentEntities.add(e);
                goldEntityMentionsByID.put(mention.id, e);
                //System.out.println("\t"+mention.id+", "+mention.extent+", "+mention.type+", "+mention.ldcType);
            }
            // Add all pairs of coreference edges
            for (int i = 0; i < coreferentEntities.size(); i++) {
                for (int j = i+1; j < coreferentEntities.size(); j++) {
                    EntityMention e1 = coreferentEntities.get(i);
                    EntityMention e2 = coreferentEntities.get(j);
                    CoreferenceEdge edge = new CoreferenceEdge(e1, e2);
                    goldCoreferenceEdges.add(edge);
                    goldCoreferenceEdgesByEntities.put(new Pair<>(e1, e2), edge);
                }
            }
        }

        for (ACERelation relation: relationList) {
            relationTypes.add(relation.type);
            System.out.println("RELATION: "+relation.id+", "+relation.type);
            for (ACERelationMention rm : relation.relationMentionList) {
                System.out.println("\trelation mention: "+rm.id);
                EntityMention e1 = null;
                EntityMention e2 = null;
                for (ACERelationArgumentMention ram: rm.relationArgumentMentionList) {
                    if (ram.role.equals(Consts.ARG_1)) {
                        //System.out.println("\t\tArg_1: "+ram.id+", "+ram.argStr);
                        e1 = goldEntityMentionsByID.get(ram.id);
                    } else if (ram.role.equals(Consts.ARG_2)) {
                        //System.out.println("\t\tArg_2: "+ram.id+", "+ram.argStr);
                        e2 = goldEntityMentionsByID.get(ram.id);
                    }
                }
                Relation rel = new Relation(relation.type, e1, e2);
                goldRelations.add(rel);
                goldRelationsByArgs.put(new Pair<>(e1, e2), rel);
            }
        }
    }

    //NOTE: because of the (incorrect) tokenization, this introduces a bit of inaccuracy into the gold labels -
    // for the time being, we can't get around this.
    private EntityMention makeEntityMention(ACEEntityMention mention, String type) {
        IntPair offsets = findTokenOffsets(mention.extentStart, mention.extentEnd);
        return new EntityMention(type, offsets.getFirst(), offsets.getSecond(), this);
    }

    public int getNumberOfSentences() {
        return sentenceTokens.size();
    }

    public Iterator<List<String>> sentenceIterator() {
        return sentenceTokens.iterator();
    }


    /**
     *
     * @return All sentences in the document
     */
    public List<List<String>> getSentences() {
        return sentenceTokens;
    }

    public List<String> getTokens() {
        List<String> result = new ArrayList<>();
        for (List<String> sentence: sentenceTokens) {
            result.addAll(sentence);
        }
        return result;
    }

    /**
     * @param ind The sentence number within the document
     * @return The list of tokens for the given sentence, or null if the index is invalid
     */
    public List<String> getSentence(int ind) {
        if (ind >= sentenceTokens.size() || ind < 0) {
            return null;
        } else {
            return sentenceTokens.get(ind);
        }
    }

    /**
     * @param ind The token number within the document
     * @return The requested token, or null if the index is invalid
     */
    public String getToken(int ind) {
        if (ind >= tokens.size() || ind < 0) {
            return null;
        } else {
            return tokens.get(ind);
        }
    }

    /**
     * NOTE: Using BIO assumes that the entity spans are non-overlapping, which isn't true in this entire dataset.
     * @return The Encodings, represented as a list of encodings per sentence
     */
    public List<List<String>> getGoldBIOEncoding() {
        if (BIOencoding != null) {
            return BIOencoding;
        }

        BIOencoding = new ArrayList<List<String>>();
        for (TextAnnotation ta: taList) {
            System.out.println(ta);
            if (!ta.hasView(EventConstants.NER_ACE_COARSE)) {
                for (Sentence sentence: ta.sentences()) {
                    List<String> labelList = new ArrayList<>();
                    BIOencoding.add(labelList);
                    for (String token: sentence.getTokens()) {
                        labelList.add(Consts.BIO_O);
                    }
                }
            }
            else {
                View nerView = ta.getView(EventConstants.NER_ACE_COARSE);
                int tokenInd = 0;

                List<Constituent> labels = nerView.getConstituents();
                System.out.println(labels);
                Iterator<Constituent> labelItr = labels.iterator();
                Constituent currentLabel = labelItr.next();
                for (Sentence sentence : ta.sentences()) {
                    List<String> labelList = new ArrayList<>();
                    BIOencoding.add(labelList);
                    for (String token : sentence.getTokens()) {
                        if (currentLabel != null && tokenInd == currentLabel.getEndSpan()) {
                            //TODO: should we go for smallest spans or largest?
                            while (currentLabel != null && currentLabel.getEndSpan() <= tokenInd) {
                                currentLabel = labelItr.hasNext() ? labelItr.next() : null;
                            }
                        }
                        if (currentLabel == null || tokenInd < currentLabel.getStartSpan()) {
                            labelList.add(Consts.BIO_O);
                        } else if (!currentLabel.doesConstituentCover(tokenInd)) {
                            System.out.println(tokenInd);
                            System.out.println(currentLabel.getSpan());
                            throw new RuntimeException("BIO ERROR");
                        } else if (tokenInd == currentLabel.getStartSpan()) {
                            labelList.add(Consts.BIO_B + currentLabel.getLabel());
                        } else if (tokenInd > currentLabel.getStartSpan() && tokenInd < currentLabel.getEndSpan()) {
                            labelList.add(Consts.BIO_I + currentLabel.getLabel());
                        }

                        tokenInd++;
                    }
                }
            }
        }
        System.out.println(BIOencoding);
        return BIOencoding;
    }

    /**
     *
     * @return A list of lists - each of these representing a sentence - of POS tags (representing the tag per
     *         word in the given sentence)
     */
    public List<List<String>> getPOSTagsBySentence() {
        List<List<String>> result = new ArrayList<List<String>>();
        for (TextAnnotation ta: taList) {
            int tokenInd = 0;
            for (Sentence sentence: ta.sentences()) {
                List<String> posList = new ArrayList<>();
                result.add(posList);
                View posView = ta.getView(ViewNames.POS);
                for (int i = 0; i < sentence.getTokens().length; i++) {
                    posList.add(posView.getLabelsCoveringToken(tokenInd++).get(0));
                }
            }
        }

        return result;
    }

    public List<String> getPOSTags() {
        List<String> result = new ArrayList<>();
        for (TextAnnotation ta: taList) {
            int tokenInd = 0;
            for (Sentence sentence: ta.sentences()) {
                View posView = ta.getView(ViewNames.POS);
                for (int i = 0; i < sentence.getTokens().length; i++) {
                    result.add(posView.getLabelsCoveringToken(tokenInd++).get(0));
                }
            }
        }
        return result;
    }

    /**
     *
     * @return A list of lists - each of these representing a sentence - of lemmas (representing the lemma per
     *         word in the given sentence)
     */
    public List<List<String>> getLemmasBySentence() {
        List<List<String>> result = new ArrayList<>();
        for (TextAnnotation ta: taList) {
            int tokenInd = 0;
            for (Sentence sentence: ta.sentences()) {
                List<String> lemmaList = new ArrayList<>();
                result.add(lemmaList);
                View lemmaView = ta.getView(ViewNames.LEMMA);
                for (int i = 0; i < sentence.getTokens().length; i++) {
                    lemmaList.add(lemmaView.getLabelsCoveringToken(tokenInd++).get(0));
                }
            }
        }

        return result;
    }

    public List<String> getLemmas() {
        List<String> result = new ArrayList<>();
        for (TextAnnotation ta: taList) {
            int tokenInd = 0;
            for (Sentence sentence: ta.sentences()) {
                View posView = ta.getView(ViewNames.LEMMA);
                for (int i = 0; i < sentence.getTokens().length; i++) {
                    result.add(posView.getLabelsCoveringToken(tokenInd++).get(0));
                }
            }
        }
        return result;
    }

    /**
     * This is the function that should be called by the NER system to add an entity mention to the test labels
     *
     * @param type The type of the entity
     * @param startOffset The index of the first token of the span containing the entity
     * @param endOffset The index of the last token + 1 (e.g. if the last token is #3, the value here should be 4)
     */
    public void addEntityMention(String type, int startOffset, int endOffset) {
        testEntityMentions.add(new EntityMention(type, startOffset, endOffset, this));
    }

    /**
     * This is the function that should be called by the relation extraction system to add a relation to the test labels
     * @param type The type of the relation
     * @param e1 The entity mention that takes the role of ARG-1 in the relation
     * @param e2 The entity mention that takes the role of ARG-2 in the relation
     */
    public void addRelation(String type, EntityMention e1, EntityMention e2) {
        testRelations.add(new Relation(type, e1, e2));
    }

    public void addCoreferenceEdge(EntityMention e1, EntityMention e2) {
        testCoreferenceEdges.add(new CoreferenceEdge(e1, e2));
    }

    public List<EntityMention> getGoldEntityMentions() {
        return goldEntityMentions;
    }

    public List<EntityMention> getTestEntityMentions() {
        return testEntityMentions;
    }

    /**
     * This method returns all of the relations that are explicitly specified within the gold data
     * @return The list of relations
     */
    public List<Relation> getGoldRelations() {
        return goldRelations;
    }

    public Map<Pair<EntityMention,EntityMention>,Relation> getGoldRelationsByArgs() {
        return goldRelationsByArgs;
    }

    /**
     * This method returns a relation for each pair of entity mentions, including NO_REL relation
     *
     * Note: Coreference "relations" are not included in this!
     * @return The pair of lists of relations; the first list contains the explicit relations, and the second
     *         list contains the "no relation" relations.
     */
    public Pair<List<Relation>,List<Relation>> getAllPairsGoldRelations() {
        List<Relation> result1 = new ArrayList<>(goldRelations);
        List<Relation> result2 = new ArrayList<Relation>(goldRelations);
        for (int e1Ind = 0; e1Ind < goldEntityMentions.size(); e1Ind++) {
            for (int e2Ind = e1Ind + 1; e2Ind < goldEntityMentions.size(); e2Ind++) {
                EntityMention e1 = goldEntityMentions.get(e1Ind);
                EntityMention e2 = goldEntityMentions.get(e2Ind);
                if (!goldRelationsByArgs.containsKey(new Pair<>(e1, e2)) &&
                        !goldRelationsByArgs.containsKey(new Pair<>(e2, e1))) {
                    result2.add(new Relation(Consts.NO_REL, e1, e2));
                }
            }
        }
        return new Pair<>(result1, result2);
    }

    /**
     * This method returns a coreference edge for each pair of entity mentions
     * @return A Pair of lists of edges; the first list contains the "true" edges, and the second
     *         list contains the "false" edges.
     */
    public Pair<List<CoreferenceEdge>, List<CoreferenceEdge>> getAllPairsGoldCoreferenceEdges() {
        List<CoreferenceEdge> result1 = new ArrayList<>(goldCoreferenceEdges);
        List<CoreferenceEdge> result2 = new ArrayList<>(goldCoreferenceEdges);
        for (int e1Ind = 0; e1Ind < goldEntityMentions.size(); e1Ind++) {
            for (int e2Ind = e1Ind + 1; e2Ind < goldEntityMentions.size(); e2Ind++) {
                EntityMention e1 = goldEntityMentions.get(e1Ind);
                EntityMention e2 = goldEntityMentions.get(e2Ind);
                if (!goldCoreferenceEdgesByEntities.containsKey(new Pair<>(e1, e2)) &&
                        !goldCoreferenceEdgesByEntities.containsKey(new Pair<>(e2, e1))) {
                    result2.add(new CoreferenceEdge(e1, e2, false));
                }
            }
        }
        return new Pair<>(result1, result2);
    }

    public List<CoreferenceEdge> getGoldCoreferenceEdges() {
        return goldCoreferenceEdges;
    }

    public Map<Pair<EntityMention,EntityMention>, CoreferenceEdge> getGoldCoreferenceEdgesByEntities() {
        return goldCoreferenceEdgesByEntities;
    }

    public List<CoreferenceEdge> getTestCoreferenceEdges() {
        return testCoreferenceEdges;
    }

    public List<String> getExtent(int start, int end) {
        int tokenCount = -1;
        List<String> result = new ArrayList<>();
        for (TextAnnotation ta: taList) {
            if (start > tokenCount + ta.getTokens().length) {
                tokenCount += ta.getTokens().length;
                continue;
            }
            for (int i = 0; i < ta.getTokens().length; i++) {
                tokenCount++;
                if (tokenCount < start) {
                    continue;
                } else if (tokenCount < end) {
                    result.add(ta.getTokens()[i]);
                } else {
                    break;
                }
            }
            if (tokenCount >= end) {
                break;
            }
        }
        return result;
    }

    /**
     * Searches through all of the text annotations to find the token offsets for a given mention
     * NOTE: due to incorrect tokenization, the mention boundaries may lie within a token; in that case, we "round"
     *
     * @param mentionStart The character offset for the start of the mention
     * @param mentionEnd The character offset for the end of the mention
     * @return an IntPair representing the start and end+1 indices of the mention.
     */
    private IntPair findTokenOffsets(int mentionStart, int mentionEnd )
    {
        int tokenStart = -1;
        int tokenEnd = -1;
        int tokenStartOffset = 0;
        int tokenEndOffset = 0;
        for (TextAnnotation ta: taList) {

            View tokenOffsetView = ta.getView(EventConstants.TOKEN_WITH_CHAR_OFFSET);


            for (Constituent t : tokenOffsetView.getConstituents()) {
                if (Integer.parseInt(t.getAttribute(EventConstants.CHAR_START)) <= mentionStart) {
                    tokenStart = t.getStartSpan();
                }
                if (tokenEnd == -1 && Integer.parseInt(t.getAttribute(EventConstants.CHAR_END)) >= mentionEnd) {
                    tokenEnd = t.getEndSpan();
                }
            }

            if (tokenStart >= 0 && tokenEnd >= 0) {
                return new IntPair(tokenStart + tokenStartOffset, tokenEnd+tokenEndOffset);
            } else {
                tokenStartOffset += ta.getTokens().length;
                tokenEndOffset += ta.getTokens().length;
            }
        }
        return null;
    }

    // Static methods - these are used to access global information relating to the dataset

    public static Set<String> getRelationTypes() {
        return relationTypes;
    }

    public static Set<String> getEntityTypes() {
        return entityTypes;
    }

    public static Set<String> getEntitySubtypes() {
        return entitySubtypes;
    }

    public static Set<String> getBIOLabels() {
        if (bioLabels != null) {
            return bioLabels;
        }
        bioLabels = new HashSet<String>();

        bioLabels.add(Consts.BIO_O);
        for (String type: getEntityTypes()) {
            bioLabels.add(Consts.BIO_B + type);
            bioLabels.add(Consts.BIO_I + type);
        }

        return bioLabels;
    }



}
