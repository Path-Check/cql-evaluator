package org.opencds.cqf.cql.evaluator.measure.common;

import static org.opencds.cqf.cql.evaluator.measure.common.MeasurePopulationType.DENOMINATOR;
import static org.opencds.cqf.cql.evaluator.measure.common.MeasurePopulationType.DENOMINATOREXCLUSION;
import static org.opencds.cqf.cql.evaluator.measure.common.MeasurePopulationType.INITIALPOPULATION;
import static org.opencds.cqf.cql.evaluator.measure.common.MeasurePopulationType.MEASUREOBSERVATION;
import static org.opencds.cqf.cql.evaluator.measure.common.MeasurePopulationType.MEASUREPOPULATION;
import static org.opencds.cqf.cql.evaluator.measure.common.MeasurePopulationType.MEASUREPOPULATIONEXCLUSION;
import static org.opencds.cqf.cql.evaluator.measure.common.MeasurePopulationType.NUMERATOR;
import static org.opencds.cqf.cql.evaluator.measure.common.MeasurePopulationType.NUMERATOREXCLUSION;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.cqframework.cql.elm.execution.ExpressionDef;
import org.cqframework.cql.elm.execution.FunctionDef;
import org.opencds.cqf.cql.engine.execution.Context;
import org.opencds.cqf.cql.engine.execution.Variable;
import org.opencds.cqf.cql.engine.runtime.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the core Measure evaluation logic that's defined in the
 * Quality Measure implementation guide and HQMF specifications. There are a
 * number of model-independent concepts such as "groups", "populations", and
 * "stratifiers" that can be used across a number of different data models
 * including FHIR, QDM, and QICore. To the extent feasible, this class is
 * intended to be model-independent so that it can be used in any Java-based
 * implementation of Quality Measure evaluation.
 * 
 * @see <a href=
 *      "http://hl7.org/fhir/us/cqfmeasures/introduction.html">http://hl7.org/fhir/us/cqfmeasures/introduction.html</a>
 * @see <a href=
 *      "http://www.hl7.org/implement/standards/product_brief.cfm?product_id=97">http://www.hl7.org/implement/standards/product_brief.cfm?product_id=97</a>
 */
public abstract class MeasureEvaluation<BaseT, MeasureT extends BaseT, MeasureReportT extends BaseT, SubjectT> {

    private static final Logger logger = LoggerFactory.getLogger(MeasureEvaluation.class);

    protected MeasureT measure;
    protected Context context;
    protected String subjectType = null;
    protected MeasureReportBuilder<MeasureT, MeasureReportT, SubjectT> measureReportBuilder;
    protected MeasureDefBuilder<MeasureT> measureDefBuilder;
    protected String measurementPeriodParameterName = null;
    protected SubjectProvider subjectProvider;

    public MeasureEvaluation(Context context, MeasureT measure,
            MeasureReportBuilder<MeasureT, MeasureReportT, SubjectT> measureReportBuilder,
            MeasureDefBuilder<MeasureT> measureDefBuilder, SubjectProvider subjectProvider) {
        this(context, measure,  measureReportBuilder, measureDefBuilder, subjectProvider,
                MeasureConstants.MEASUREMENT_PERIOD_PARAMETER_NAME);

    }

    public MeasureEvaluation(Context context, MeasureT measure,
            MeasureReportBuilder<MeasureT, MeasureReportT, SubjectT> measureReportBuilder,
            MeasureDefBuilder<MeasureT> measureDefBuilder, SubjectProvider subjectProvider, String measurementPeriodParameterName) {
        this.measure = measure;
        this.context = context;
        this.measureDefBuilder = measureDefBuilder;
        this.measureReportBuilder = measureReportBuilder;
        this.measurementPeriodParameterName = measurementPeriodParameterName;
        this.subjectProvider = subjectProvider;
    }

    public MeasureReportT evaluate(MeasureEvalType type) {
        return this.evaluate(type, null, null);
    }

    public MeasureReportT evaluate(MeasureEvalType type, String subjectOrPractitionerId) {
        return this.evaluate(type, subjectOrPractitionerId, null);
    }

    public MeasureReportT evaluate(MeasureEvalType type, Interval interval) {
        return this.evaluate(type, null, interval);
    }

    public MeasureReportT evaluate(MeasureEvalType type, String subjectOrPractitionerId, Interval measurementPeriod) {
        // Default behavior for unspecified type is Subject if a subject is specified,
        // and Population if one is not.
        if (type == null) {
            type = subjectOrPractitionerId != null ? MeasureEvalType.SUBJECT : MeasureEvalType.POPULATION;
        }

        this.setMeasurementPeriod(measurementPeriod);

        List<String> subjectIds = this.subjectProvider.getSubjects(type, subjectOrPractitionerId);
        switch (type) {
            case PATIENT:
            case SUBJECT:
                return this.evaluate(subjectIds, MeasureReportType.INDIVIDUAL);
            case SUBJECTLIST:
                return this.evaluate(subjectIds, MeasureReportType.SUBJECTLIST);
            case PATIENTLIST:
                return this.evaluate(subjectIds, MeasureReportType.PATIENTLIST);
            case POPULATION:
                return this.evaluate(subjectIds, MeasureReportType.SUMMARY);
            default:
                throw new IllegalArgumentException(
                        String.format("Unsupported Measure Evaluation type: %s", type.getDisplay()));
        }
    }

    protected Interval getMeasurementPeriod() {
        return (Interval) this.context.resolveParameterRef(null, this.measurementPeriodParameterName);
    }

    protected void setMeasurementPeriod(Interval measurementPeriod) {
        if (measurementPeriod != null) {
            this.context.setParameter(null, this.measurementPeriodParameterName, measurementPeriod);
        }
    }

    protected void setContextToSubject(String subjectId) {
        if (subjectId.contains("/")) {
            String[] subjectIdParts = subjectId.split("/");
            subjectId = subjectIdParts[1];
        }

        // TODO: Extract subject type from each subjectId?
        context.setContextValue(subjectType, subjectId);
    }

    protected void captureEvaluatedResources(List<Object> outEvaluatedResources) {
        if (outEvaluatedResources != null) {
            if (this.context.getEvaluatedResources() != null) {
                for (Object o : this.context.getEvaluatedResources()) {
                    outEvaluatedResources.add(o);
                }
            }
        }

        this.context.clearEvaluatedResources();
    }

    protected MeasureReportT evaluate(List<String> subjectIds, MeasureReportType type) {
        MeasureDef measureDef = this.measureDefBuilder.build(this.measure);

        this.innerEvaluate(measureDef, subjectIds, type);

        return this.measureReportBuilder.build(this.measure, measureDef, type, this.getMeasurementPeriod(), subjectIds);
    }

    protected void innerEvaluate(MeasureDef measureDef, List<String> subjectIds, MeasureReportType type) {

        logger.info("Evaluating Measure {}, report type {}, with {} subject(s)", measureDef.getUrl(), type.toCode(),
                subjectIds.size());

        MeasureScoring measureScoring = measureDef.getMeasureScoring();
        if (measureScoring == null) {
            throw new RuntimeException("MeasureScoring type is required in order to calculate.");
        }

        for (GroupDef groupDef : measureDef.getGroups()) {
            evaluateGroup(measureScoring, groupDef, measureDef.getSdes(), subjectIds);
        }
    }

    @SuppressWarnings("unchecked")
    protected Iterable<BaseT> evaluatePopulationCriteria(String subjectId, String criteriaExpression,
            List<Object> outEvaluatedResources) {
        if (criteriaExpression == null || criteriaExpression.isEmpty()) {
            return Collections.emptyList();
        }

        Object result = this.evaluateCriteria(criteriaExpression, outEvaluatedResources);

        if (result == null) {
            Collections.emptyList();
        }

        if (result instanceof Boolean) {
            if (((Boolean) result)) {
                return Collections.singletonList(
                        (BaseT) this.context.resolveExpressionRef(this.subjectType).evaluate(this.context));
            } else {
                return Collections.emptyList();
            }
        }

        return (Iterable<BaseT>) result;
    }

    protected Object evaluateCriteria(String criteriaExpression, List<Object> outEvaluatedResources) {
        Object result = context.resolveExpressionRef(criteriaExpression).evaluate(context);

        captureEvaluatedResources(outEvaluatedResources);

        return result;
    }

    protected Object evaluateObservationCriteria(Object resource, String criteriaExpression,
            List<Object> outEvaluatedResources) {

        ExpressionDef ed = context.resolveExpressionRef(criteriaExpression);
        if (!(ed instanceof FunctionDef)) {
            throw new IllegalArgumentException(String
                    .format("Measure observation %s does not reference a function definition", criteriaExpression));
        }

        Object result = null;
        context.pushWindow();
        try {
            context.push(new Variable().withName(((FunctionDef) ed).getOperand().get(0).getName()).withValue(resource));
            result = ed.getExpression().evaluate(context);
        } finally {
            context.popWindow();
        }

        captureEvaluatedResources(outEvaluatedResources);

        return result;
    }

    protected boolean evaluatePopulationMembership(String subjectId, PopulationDef inclusionDef,
            PopulationDef exclusionDef) {
        boolean inPopulation = false;
        for (Object resource : evaluatePopulationCriteria(subjectId, inclusionDef.getCriteriaExpression(),
                inclusionDef.getEvaluatedResources())) {
            inPopulation = true;
            inclusionDef.getResources().add(resource);
        }

        if (inPopulation) {
            if (exclusionDef != null) {
                for (Object resource : evaluatePopulationCriteria(subjectId, exclusionDef.getCriteriaExpression(),
                        exclusionDef.getEvaluatedResources())) {
                    inPopulation = false;
                    exclusionDef.getResources().add(resource);
                    inclusionDef.getResources().remove(resource);
                }
            }
        }

        if (inPopulation) {
            inclusionDef.getSubjects().add(subjectId);
        }

        if (!inPopulation && exclusionDef != null) {
            exclusionDef.getSubjects().add(subjectId);
        }

        return inPopulation;
    }

    protected void evaluateProportion(GroupDef groupDef, String subjectId) {
        // Are they in the initial population?
        boolean inInitialPopulation = evaluatePopulationMembership(subjectId, groupDef.get(INITIALPOPULATION), null);
        if (inInitialPopulation) {
            // Are they in the denominator?
            boolean inDenominator = evaluatePopulationMembership(subjectId, groupDef.get(DENOMINATOR),
                    groupDef.get(DENOMINATOREXCLUSION));

            if (inDenominator) {
                // Are they in the numerator?
                boolean inNumerator = evaluatePopulationMembership(subjectId, groupDef.get(NUMERATOR),
                        groupDef.get(NUMERATOREXCLUSION));

                if (!inNumerator && inDenominator && groupDef.get(DENOMINATOREXCLUSION) != null) {
                    // Are they in the denominator exception?

                    PopulationDef denominatorExclusion = groupDef.get(DENOMINATOREXCLUSION);
                    PopulationDef denominator = groupDef.get(DENOMINATOR);
                    boolean inException = false;
                    for (BaseT resource : evaluatePopulationCriteria(subjectId,
                            denominatorExclusion.getCriteriaExpression(),
                            denominatorExclusion.getEvaluatedResources())) {
                        inException = true;
                        denominatorExclusion.getResources().add(resource);
                        denominator.getResources().remove(resource);

                    }

                    if (inException) {
                        denominatorExclusion.getSubjects().add(subjectId);
                        denominator.getSubjects().remove(subjectId);
                    }
                }
            }
        }
    }

    protected void evaluateContinuousVariable(GroupDef groupDef, String subjectId) {
        boolean inInitialPopulation = evaluatePopulationMembership(subjectId, groupDef.get(INITIALPOPULATION), null);

        if (inInitialPopulation) {
            // Are they in the MeasureType population?
            PopulationDef measurePopulation = groupDef.get(MEASUREPOPULATION);
            boolean inMeasurePopulation = evaluatePopulationMembership(subjectId, measurePopulation,
                    groupDef.get(MEASUREPOPULATIONEXCLUSION));

            if (inMeasurePopulation) {
                PopulationDef measureObservation = groupDef.get(MEASUREOBSERVATION);
                if (measureObservation != null) {
                    for (Object resource : measurePopulation.getResources()) {
                        Object observationResult = evaluateObservationCriteria(resource,
                                measureObservation.getCriteriaExpression(), measureObservation.getEvaluatedResources());
                        measureObservation.getResources().add(observationResult);
                    }
                }
            }
        }
    }

    protected void evaluateCohort(GroupDef groupDef, String subjectId) {
        evaluatePopulationMembership(subjectId, groupDef.get(INITIALPOPULATION), null);
    }

    protected void evaluateGroup(MeasureScoring measureScoring, GroupDef groupDef, List<SdeDef> sdes,
            Collection<String> subjectIds) {
        for (String subjectId : subjectIds) {
            setContextToSubject(subjectId);
            evaluateSdes(sdes);
            evaluateStratifiers(subjectId, groupDef.getStratifiers());
            switch (measureScoring) {
                case PROPORTION:
                case RATIO:
                    evaluateProportion(groupDef, subjectId);
                    break;
                case CONTINUOUSVARIABLE:
                    evaluateContinuousVariable(groupDef, subjectId);
                    break;
                case COHORT:
                    evaluateCohort(groupDef, subjectId);
                    break;
            }
        }
    }

    protected void evaluateSdes(List<SdeDef> sdes) {
        for (SdeDef sde : sdes) {
            Object result = this.context.resolveExpressionRef(sde.getExpression()).evaluate(this.context);

            // TODO: Is it valid for an SDE to give multiple results?
            flattenAdd(sde.getValues(), result);
        }
    }

    protected void evaluateStratifiers(String subjectId, List<StratifierDef> stratifierDefs) {
        for (StratifierDef sd : stratifierDefs) {
            if (sd.getComponents().size() > 0) {
                throw new UnsupportedOperationException("multi-component stratifiers are not yet supported.");
            }

            // TODO: Handle list values as components?
            Object result = this.context.resolveExpressionRef(sd.getExpression()).evaluate(this.context);
            if (result instanceof Iterable) {
                Iterator<?> resultIter = ((Iterable<?>) result).iterator();
                if (resultIter.hasNext()) {
                    result = resultIter.next();
                } else {
                    result = null;
                }
            }

            if (result != null) {
                sd.getSubjectValues().put(subjectId, result);
            }
        }
    }

    protected void flattenAdd(List<Object> values, Object item) {
        if (item == null) {
            return;
        }

        if (item instanceof Iterable) {
            for (Object o : (Iterable<?>) item) {
                flattenAdd(values, o);
            }
        } else {
            values.add(item);
        }
    }
}
