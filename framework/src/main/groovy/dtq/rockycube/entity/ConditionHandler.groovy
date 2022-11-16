package dtq.rockycube.entity

import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityException
import org.moqui.impl.ViUtilities
import org.moqui.impl.entity.condition.ConditionField
import org.moqui.impl.entity.condition.EntityConditionImplBase
import org.moqui.impl.entity.condition.FieldValueCondition
import org.moqui.impl.entity.condition.ListCondition

import java.util.regex.Pattern

class ConditionHandler {
    // good old recursion once again
    // we need to process special string and return complex condition out of it
    public static EntityConditionImplBase recCondition(String ruleIn, ArrayList term)
    {
        // if it's simple component, a number, return baseCondition
        def recSingle = Pattern.compile("^(\\d)\$")
        def mSingle = recSingle.matcher(ruleIn)
        if (mSingle)
        {
            def groupNum = mSingle.group(1).toString().toInteger()
            HashMap<String, Object> fieldCond = term.get(groupNum - 1)
            return getSingleFieldCondition(fieldCond)
        }

        // use regex to search for both OPERATOR and CONTENT itself
        def rec = Pattern.compile("^(AND|OR)\\((.+)\\)")
        def m = rec.matcher(ruleIn)
        boolean isEntireListCond = m.matches()
        // 1. if string matches pattern from above, then it's the entire condition
        // 2. if not, than we may have a list in place
        if (!isEntireListCond)
        {
            // then, split them using comma and return one by one
            def items = ViUtilities.splitWithBracketsCheck(ruleIn, ",")

            List<EntityCondition> res = new ArrayList()
            items.each {it->
                res.add(recCondition(it, term))
            }
            return res as EntityConditionImplBase
        }

        def joinOp = EntityCondition.JoinOperator.AND
        switch (m.group(1))
        {
            case "OR":
                joinOp = EntityCondition.JoinOperator.OR
                break
        }
        def entireCond = new ListCondition(recCondition(m.group(2), term), joinOp)
        return entireCond
    }

    public static List<FieldValueCondition> getFieldsCondition(List<HashMap<String, Object>> term)
    {
        List<FieldValueCondition> res = new ArrayList<FieldValueCondition>()
        for (def t in term) {
            res.add(getSingleFieldCondition((HashMap) t))
        }
        return res
    }

    public static boolean evaluateCondition(FieldValueCondition cond, Object item){
        def val = item.getAt(cond.fieldName)
        def condVal = cond.value

        switch (cond.operator){
            case EntityCondition.ComparisonOperator.EQUALS:
                return val == condVal
            case EntityCondition.ComparisonOperator.NOT_EQUAL:
                return val != condVal
            case EntityCondition.ComparisonOperator.IN:
                if (!val) return false
                def isArr = condVal.getClass().isArray() || condVal.getClass().name == "java.util.ArrayList"
                if (!isArr) throw new EntityException("Comparison value is not of type Array when using 'IN' operator")
                ArrayList condArr = (ArrayList) condVal
                return condArr.contains(val)
            case EntityCondition.ComparisonOperator.NOT_IN:
                if (!val) return false
                def isArr = condVal.getClass().isArray() || condVal.getClass().name == "java.util.ArrayList"
                if (!isArr) throw new EntityException("Comparison value is not of type Array when using 'NOT_IN' operator")
                ArrayList condArr = (ArrayList) condVal
                return !condArr.contains(val)
            case EntityCondition.ComparisonOperator.LIKE:
                // if null, return false
                if (!val) return false
                def rec = cond.value
                def recLike = Pattern.compile(rec as String)
                return recLike.matcher(val).matches()
            case EntityCondition.ComparisonOperator.NOT_LIKE:
                // if null, return false
                if (!val) return false
                def rec = cond.value
                def recLike = Pattern.compile(rec as String)
                return !recLike.matcher(val).matches()
            default:
                throw new EntityException("Operator [${cond.operator}] not supported when evaluating condition")
        }
    }

    public static FieldValueCondition getSingleFieldCondition(HashMap singleTerm)
    {
        // check required fields
        if (!singleTerm.containsKey("field")) throw new EntityException("Field condition not set correctly, 'field' missing [${singleTerm}]")
        if (!singleTerm.containsKey("value")) throw new EntityException("Field condition not set correctly, 'value' missing [${singleTerm}]")

        def compOperator = EntityCondition.ComparisonOperator.EQUALS

        // logger.debug("singleTerm.value.getClass() = ${singleTerm.value.getClass().simpleName}")
        if (singleTerm.containsKey("operator"))
        {
            switch(singleTerm["operator"].toString().toLowerCase())
            {
                case "=":
                case "eq":
                case "equal":
                case "equals":
                    // do nothing
                    break
                case "!=":
                case "not-equal":
                    compOperator = EntityCondition.ComparisonOperator.NOT_EQUAL
                    break
                case ">":
                case "gt":
                    compOperator = EntityCondition.ComparisonOperator.GREATER_THAN
                    break
                case ">=":
                case "gte":
                    compOperator = EntityCondition.ComparisonOperator.GREATER_THAN_EQUAL_TO
                    break
                case "<":
                case "lt":
                    compOperator = EntityCondition.ComparisonOperator.LESS_THAN
                    break
                case "<=":
                case "lte":
                    compOperator = EntityCondition.ComparisonOperator.LESS_THAN_EQUAL_TO
                    break
                case "like":
                    compOperator = EntityCondition.ComparisonOperator.LIKE
                    break
                case "not-like":
                case "not_like":
                    compOperator = EntityCondition.ComparisonOperator.NOT_LIKE
                    break
                case "in":
                    compOperator = EntityCondition.ComparisonOperator.IN
                    if (singleTerm.value.getClass().simpleName != "ArrayList") throw new EntityException("Operator requires List value, but was not provided")
                    break
                case "not-in":
                case "not_in":
                    compOperator = EntityCondition.ComparisonOperator.NOT_IN
                    if (singleTerm.value.getClass().simpleName != "ArrayList") throw new EntityException("Operator requires List value, but was not provided")
                    break
                case "between":
                    compOperator = EntityCondition.ComparisonOperator.BETWEEN
                    if (singleTerm.value.getClass().simpleName != "ArrayList") throw new EntityException("Operator requires List value, but was not provided")
                    if (singleTerm.value.size() != 2) throw new EntityException("Operator requires exactly two values in array")
                    break
                case "not-between":
                case "not_between":
                    compOperator = EntityCondition.ComparisonOperator.NOT_BETWEEN
                    if (singleTerm.value.getClass().simpleName != "ArrayList") throw new EntityException("Operator requires List value, but was not provided")
                    if (singleTerm.value.size() != 2) throw new EntityException("Operator requires exactly two values in array")
                    break
                case "text":
                    compOperator = EntityCondition.ComparisonOperator.TEXT
                    break
            }
        }

        return new FieldValueCondition(
                new ConditionField((String) singleTerm.field),
                compOperator,
                (Object) singleTerm.value
        )
    }
}
