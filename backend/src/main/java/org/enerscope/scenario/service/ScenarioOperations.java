package org.enerscope.scenario.service;
import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.enerscope.common.BaseEntity;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.service.NodeCloner;
import org.enerscope.probabilistic.*;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/** Only physical scalar fields are writable; associations, costs, IDs and geometry stay server-owned. */
@Service @RequiredArgsConstructor
public class ScenarioOperations {
 private final ObjectMapper mapper;
 private List<java.lang.reflect.Field> fields(BaseNode node) {
  var result=new ArrayList<java.lang.reflect.Field>();
  for(Class<?> c=org.hibernate.Hibernate.getClass(node);c!=BaseEntity.class;c=c.getSuperclass())
   for(var f:c.getDeclaredFields()) {
    var t=f.getType();
    if(!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic() &&
       (t==int.class || t==float.class || t==double.class || t==Instant.class ||
        t==org.enerscope.node.model.enums.NodeStateEnum.class || f.getName().equals("name") ||
        ProbabilisticDistribution.class.isAssignableFrom(t))) { f.setAccessible(true);result.add(f); }
   }
  result.sort(Comparator.comparing(java.lang.reflect.Field::getName));
  return result;
 }
 public OperationalNode describe(BaseNode node) {
  var values=new TreeMap<String,JsonNode>();
  var definitions=new ArrayList<ScenarioConfigurationDTO.Field>();
  for(var field:fields(node)) {
   Object value=get(field,node);
   String type=field.getType()==int.class ? "integer" : field.getType()==float.class || field.getType()==double.class ? "number" :
      field.getType()==Instant.class ? "instant" : field.getType().isEnum() ? "state" :
      ProbabilisticDistribution.class.isAssignableFrom(field.getType()) ? "distribution" : "text";
   definitions.add(new ScenarioConfigurationDTO.Field(field.getName(),type));
   values.put(field.getName(), type.equals("distribution") ? distribution((ProbabilisticDistribution)value) : mapper.valueToTree(value instanceof Instant ? value.toString() : value));
  }
  return new OperationalNode(node.getId(),node.getIdentityId(),node.getName(),node.getType().getNodeType().name(),definitions,values);
 }
 private Object get(java.lang.reflect.Field field,Object value) {
  try { return field.get(value); } catch(IllegalAccessException e) { throw new IllegalStateException(e); }
 }
 private float parameter(ProbabilisticDistribution value,String name) {
  try { var f=org.hibernate.Hibernate.getClass(value).getDeclaredField(name);f.setAccessible(true);return f.getFloat(value); }
  catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
 }
 private JsonNode distribution(ProbabilisticDistribution value) {
  if(value==null) return mapper.nullNode();
  var n=mapper.createObjectNode();
  if(value instanceof ConstantValue) return n.put("type","CONSTANT").put("a",parameter(value,"value"));
  if(value instanceof NormalDistributionCase) return n.put("type","NORMAL").put("a",parameter(value,"media")).put("b",parameter(value,"constantDistribution"));
  if(value instanceof UniformDistributionCase) return n.put("type","UNIFORM").put("a",parameter(value,"min_val")).put("b",parameter(value,"max_val"));
  throw new IllegalArgumentException("Unsupported distribution");
 }
 public BaseNode prepare(BaseNode original,Map<String,JsonNode> values) {
  if(values==null || !values.keySet().equals(describe(original).values().keySet()))
   throw new IllegalArgumentException("Incomplete or unknown operational fields: "+original.getName());
  BaseNode copy=NodeCloner.cloneNode(original);
  if(!original.isActive()) copy.deactivate();
  for(var field:fields(copy)) {
   JsonNode n=values.get(field.getName()); var type=field.getType();
   try {
    if(type==Instant.class) field.set(copy,n==null || n.isNull() || n.asText().isBlank() ? null : Instant.parse(n.asText()));
    else if(type==String.class) { if(n==null || !n.isTextual() || n.asText().isBlank() || n.asText().length()>320) throw new IllegalArgumentException("Required node name");field.set(copy,n.asText()); }
    else if(type.isEnum()) {
     String state=n.asText();
     if(!Set.of("PROPOSED","PENDING","RUNNING").contains(state) && !state.equals(original.getState().name())) throw new IllegalArgumentException("Invalid node state");
     field.set(copy,org.enerscope.node.model.enums.NodeStateEnum.valueOf(state));
    } else if(ProbabilisticDistribution.class.isAssignableFrom(type)) {
     if(n==null || n.isNull()) throw new IllegalArgumentException("Distribution is required");
     float a=number(n.get("a"),false), b=n.has("b") ? number(n.get("b"),false) : 0;
     ProbabilisticDistribution d=switch(n.path("type").asText()) {
      case "CONSTANT" -> new ConstantValue(a);
      case "NORMAL" -> { if(b<=0) throw new IllegalArgumentException("Standard deviation must be positive");yield new NormalDistributionCase(a,b); }
      case "UNIFORM" -> { if(a>=b) throw new IllegalArgumentException("Minimum must be below maximum");yield new UniformDistributionCase(a,b); }
      default -> throw new IllegalArgumentException("Unknown distribution");
     };
     d.sampler(0);field.set(copy,d);
    } else {
     double value=number(n,true);
     if(type==double.class) value=Double.parseDouble(n.asText().replace(',','.'));
     if(field.getName().equals("gasRichness") && value>1) throw new IllegalArgumentException("Gas richness must be between zero and one");
     if(type==int.class) {
      var decimal=new java.math.BigDecimal(n.asText().replace(',','.'));
      field.setInt(copy,decimal.intValueExact());
     } else if(type==float.class) field.setFloat(copy,(float)value);
     else field.setDouble(copy,value);
    }
   } catch(RuntimeException | IllegalAccessException e) { throw new IllegalArgumentException(original.getName()+" / "+field.getName()+": "+e.getMessage(),e); }
  }
  return copy;
 }
 private float number(JsonNode n,boolean nonnegative) {
  if(n==null || n.isNull() || n.asText().isBlank()) throw new IllegalArgumentException("Number is required");
  float value=Float.parseFloat(n.asText().replace(',','.'));
  if(!Float.isFinite(value) || (nonnegative && value<0)) throw new IllegalArgumentException("Invalid number");
  return value;
 }
}
