package com.cairone.olingo.ext.jpa.visitors;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriInfoResource;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.UriResourcePrimitiveProperty;
import org.apache.olingo.server.api.uri.queryoption.expression.BinaryOperatorKind;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor;
import org.apache.olingo.server.api.uri.queryoption.expression.Literal;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;
import org.apache.olingo.server.api.uri.queryoption.expression.MethodKind;
import org.apache.olingo.server.api.uri.queryoption.expression.UnaryOperatorKind;

import com.cairone.olingo.ext.jpa.annotations.EdmEnum;
import com.cairone.olingo.ext.jpa.annotations.EdmProperty;
import com.cairone.olingo.ext.jpa.annotations.ODataJPAProperty;
import com.cairone.olingo.ext.jpa.converters.BinaryOperatorConverter;
import com.cairone.olingo.ext.jpa.enums.BinaryOperatorGroup;
import com.cairone.olingo.ext.jpa.enums.EnumerationTreatedAs;
import com.cairone.olingo.ext.jpa.interfaces.OdataEnum;
import com.google.common.base.CharMatcher;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class FilterExpressionVisitor implements ExpressionVisitor<Object> {
	
	private static String DATE_FORMAT = "yyyy-MM-dd";
	
	private Class<?> clazz;
	private Map<String, Object> queryParams = null;
	private Map<String, String> types = new HashMap<String, String>();
	
	private int paramCount = 0;

	public FilterExpressionVisitor(Class<?> clazz, Map<String, Object> queryParams) {
		super();
		this.clazz = clazz;
		this.queryParams = queryParams;
	}

	@Override
	public Object visitBinaryOperator(BinaryOperatorKind operator, Object left, Object right) throws ExpressionVisitException, ODataApplicationException {
		
		BinaryOperatorGroup binaryOperatorGroup = BinaryOperatorGroup.from(operator);
		BinaryOperatorConverter converter = new BinaryOperatorConverter();
		
		StringBuilder sb = new StringBuilder();
		
		if(binaryOperatorGroup.equals(BinaryOperatorGroup.LOGICAL_OPERATOR)) {
			
			sb.append(left.toString());
			sb.append(converter.convertToJpqlOperator(operator));
			sb.append(right.toString());
			
			return sb.toString();
		}
		
		String param = "value" + (paramCount++);
		
		sb.append(left.toString());
		sb.append(converter.convertToJpqlOperator(operator) + ":");
		sb.append(param);
		
		String typeName = types.get(left.toString());

		if(typeName.equals("java.lang.Integer")) {
			Integer intParam = Ints.tryParse(right.toString());
			if(intParam != null) {
				queryParams.put(param, intParam);
				return sb.toString();
			}
		} else if(typeName.equals("java.lang.Long")) {
			Long longParam = Longs.tryParse(right.toString());
			if(longParam != null) {
				queryParams.put(param, longParam);
				return sb.toString();
			}
		} else if(typeName.equals("java.time.LocalDate")) {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
			try
			{
				LocalDate date = LocalDate.parse(right.toString(), formatter);
				
				queryParams.put(param, date);
				return sb.toString();
				
			} catch(DateTimeParseException e) {
				throw new ODataApplicationException(e.getMessage(), HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
			}
		} else if(typeName.equals("java.lang.String")) {
			queryParams.put(param, CharMatcher.is('\'').trimFrom(right.toString()));
		} else {
			queryParams.put(param, right);
		}
		
		return sb.toString();
	}

	@Override
	public Object visitUnaryOperator(UnaryOperatorKind operator, Object operand) throws ExpressionVisitException, ODataApplicationException {
		
		if(operator.equals(UnaryOperatorKind.NOT)) {
			return String.format("NOT (%s)", operand.toString());
		}
		return operand == null ? null : operand.toString();
	}

	@Override
	public Object visitMethodCall(MethodKind methodCall, List<Object> parameters) throws ExpressionVisitException, ODataApplicationException {
		
		StringBuilder sb = new StringBuilder();
		
		if(methodCall.equals(MethodKind.CONTAINS) || methodCall.equals(MethodKind.STARTSWITH) || methodCall.equals(MethodKind.ENDSWITH)) {
			if(parameters.get(0) instanceof String && parameters.get(1) instanceof String) {
				
				String propertyName = (String) parameters.get(0);
				if(propertyName.startsWith("e.")) propertyName = propertyName.substring(2, propertyName.length());
				updatePropertyName(propertyName);
				
				String propertyValue = (String) parameters.get(1);
				String param = "value" + (paramCount++);
				
				sb.append("e.").append(propertyName).append(" LIKE :").append(param);
				
				if(methodCall.equals(MethodKind.CONTAINS)) queryParams.put(param, CharMatcher.is('\'').replaceFrom(propertyValue, '%'));
				if(methodCall.equals(MethodKind.STARTSWITH)) queryParams.put(param, CharMatcher.is('\'').trimFrom(propertyValue) + "%");
				if(methodCall.equals(MethodKind.ENDSWITH)) queryParams.put(param, "%" + CharMatcher.is('\'').trimFrom(propertyValue));
				
				return sb.toString();
			} else {
				throw new ODataApplicationException("Contains needs two parametes of type Edm.String", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
			}
		} else if(methodCall.equals(MethodKind.DAY) || methodCall.equals(MethodKind.MONTH) || methodCall.equals(MethodKind.YEAR)) {
			if(parameters.get(0) instanceof String) {
				
				String propertyName = (String) parameters.get(0);
				if(propertyName.startsWith("e.")) propertyName = propertyName.substring(2, propertyName.length());
				updatePropertyName(propertyName);
				
				sb.append(methodCall.toString().toUpperCase()).append("(e.").append(propertyName).append(")");

				return sb.toString();
			} else {
				throw new ODataApplicationException("Day needs one parameter of type Edm.String", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
			}
		} else {
			throw new ODataApplicationException("Method call " + methodCall + " not implemented",
					HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
		}
	}

	@Override
	public Object visitLambdaExpression(String lambdaFunction, String lambdaVariable, Expression expression) throws ExpressionVisitException, ODataApplicationException {
		return null;
	}

	@Override
	public Object visitLiteral(Literal literal) throws ExpressionVisitException, ODataApplicationException {
		return literal.getText();
	}
	
	@Override
	public Object visitMember(Member member) throws ExpressionVisitException, ODataApplicationException {
		
		UriInfoResource uriInfoResource = member.getResourcePath();
		final List<UriResource> uriResourceParts = uriInfoResource.getUriResourceParts();
		
		Class<?> cl = clazz;
		List<String> segments = new ArrayList<String>();
		String canonicalName = null;
		
		for(UriResource uriResource : uriResourceParts) {
			
			if(uriResource instanceof UriResourceNavigation) {
				
				EdmNavigationProperty edmNavigationProperty = ((UriResourceNavigation) uriResource).getProperty();
				String navPropName = edmNavigationProperty.getName();
				for(Field field : cl.getDeclaredFields()) {
					com.cairone.olingo.ext.jpa.annotations.EdmNavigationProperty annEdmNavigationProperty = field.getAnnotation(com.cairone.olingo.ext.jpa.annotations.EdmNavigationProperty.class);
					if(annEdmNavigationProperty != null && (annEdmNavigationProperty.name().equals(navPropName) || field.getName().equals(navPropName))) {
						ODataJPAProperty oDataJPAProperty = field.getAnnotation(ODataJPAProperty.class);
						if(oDataJPAProperty != null && !oDataJPAProperty.value().isEmpty()) {
							cl = field.getType();
							segments.add(oDataJPAProperty.value());
						} else if(oDataJPAProperty == null) {
							cl = field.getType();
							segments.add(field.getName());
						}
					}
				}
			}
			
			if(uriResource instanceof UriResourcePrimitiveProperty) {
				UriResourcePrimitiveProperty uriResourceProperty = (UriResourcePrimitiveProperty) uriResource;
				String propertyName = uriResourceProperty.getProperty().getName();
				for(Field field : cl.getDeclaredFields()) {
					EdmProperty annEdmProperty = field.getAnnotation(EdmProperty.class);
					if(annEdmProperty != null && (annEdmProperty.name().equals(propertyName) || field.getName().equals(propertyName))) {
						canonicalName = field.getType().getCanonicalName();
						ODataJPAProperty oDataJPAProperty = field.getAnnotation(ODataJPAProperty.class);
						if(oDataJPAProperty != null && !oDataJPAProperty.value().isEmpty()) {
							propertyName = oDataJPAProperty.value();
							if(field.getType().isEnum() && oDataJPAProperty.treatedAs().equals(EnumerationTreatedAs.NUMERIC)) {
								canonicalName = "java.lang.Integer";
							} else if(field.getType().isEnum() && oDataJPAProperty.treatedAs().equals(EnumerationTreatedAs.NAME)) {
								canonicalName = "java.lang.String";
							}
						} else if(oDataJPAProperty == null) {
							propertyName = field.getName();
						}
						segments.add(propertyName);
					}
				}
			}
		}
		
		String rv = segments.isEmpty() ? null : segments.stream().map(String::toString).collect(Collectors.joining("."));
		
		if(rv == null) {
			throw new ODataApplicationException("NO SEGMENTS IN RESOURCE PATH", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
		} else {
			rv = "e." + rv;
			types.put(rv, canonicalName);
		}
		
		return rv;
	}
	
	@Override
	public Object visitAlias(String aliasName) throws ExpressionVisitException, ODataApplicationException {
		return null;
	}

	@Override
	public Object visitTypeLiteral(EdmType type) throws ExpressionVisitException, ODataApplicationException {
		return null;
	}

	@Override
	public Object visitLambdaReference(String variableName) throws ExpressionVisitException, ODataApplicationException {
		return null;
	}

	@Override
	public Object visitEnum(EdmEnumType type, List<String> enumValues) throws ExpressionVisitException, ODataApplicationException {
		
		for(Field field : clazz.getDeclaredFields()) {
			
			EdmProperty annEdmProperty = field.getAnnotation(EdmProperty.class);
			Class<?> cl = field.getType();
			
			if(cl.isEnum() && annEdmProperty != null) {
				
				EdmEnum edmEnum = cl.getAnnotation(EdmEnum.class);
				
				FullQualifiedName fqn = new FullQualifiedName(
						edmEnum.namespace(), 
						edmEnum.name().isEmpty() ? cl.getSimpleName() : edmEnum.name());
				
				if(fqn.equals(type.getFullQualifiedName())) {
					Object[] constants = cl.getEnumConstants();
					for(Object object : constants) {
						if(enumValues.contains(object.toString())) {
							ODataJPAProperty oDataJPAProperty = field.getAnnotation(ODataJPAProperty.class);
							if(oDataJPAProperty == null || oDataJPAProperty.treatedAs().equals(EnumerationTreatedAs.ENUMERATION)) {
								return object;
							} else if(oDataJPAProperty.treatedAs().equals(EnumerationTreatedAs.NAME)) {
								return object.toString();
							} else if(oDataJPAProperty.treatedAs().equals(EnumerationTreatedAs.NUMERIC)) {
								OdataEnum<?> odataEnum = (OdataEnum<?>) object;
								return Integer.valueOf(odataEnum.getOrdinal());
							}
						}
					}
				}
			}
		}
		
		return null;
	}
	
	private void updatePropertyName(String propertyName) throws ODataApplicationException {

		EdmProperty edmProperty = null;
		ODataJPAProperty oDataJPAProperty = null;
		
		for(Field field : clazz.getDeclaredFields()) {
			EdmProperty annEdmProperty = field.getAnnotation(EdmProperty.class);
			if(annEdmProperty != null && (annEdmProperty.name().equals(propertyName) || field.getName().equals(propertyName))) {
				edmProperty = annEdmProperty;
				oDataJPAProperty = field.getAnnotation(ODataJPAProperty.class);
				break;
			}
		}

		if(edmProperty == null) {
			throw new ODataApplicationException("Property not found on entity", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
		} else {
			if(oDataJPAProperty != null && !oDataJPAProperty.value().isEmpty()) {
				propertyName = oDataJPAProperty.value();
			}
		}
	}
}
