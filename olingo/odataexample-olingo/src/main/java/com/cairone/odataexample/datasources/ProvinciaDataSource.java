package com.cairone.odataexample.datasources;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityManagerFactory;

import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.FilterOption;
import org.apache.olingo.server.api.uri.queryoption.OrderByOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.cairone.odataexample.dtos.ProvinciaFrmDto;
import com.cairone.odataexample.dtos.validators.ProvinciaFrmDtoValidator;
import com.cairone.odataexample.edm.resources.ProvinciaEdm;
import com.cairone.odataexample.entities.ProvinciaEntity;
import com.cairone.odataexample.services.ProvinciaService;
import com.cairone.odataexample.utils.SQLExceptionParser;
import com.cairone.odataexample.utils.ValidatorUtil;
import com.cairone.olingo.ext.jpa.interfaces.DataSource;
import com.cairone.olingo.ext.jpa.query.JPQLQuery;
import com.cairone.olingo.ext.jpa.query.JPQLQueryBuilder;

@Component
public class ProvinciaDataSource implements DataSource {
	
	private static final String ENTITY_SET_NAME = "Provincias";
	
	@Autowired private ProvinciaService provinciaService = null;
	@Autowired private ProvinciaFrmDtoValidator provinciaFrmDtoValidator = null;

	@Autowired
	private MessageSource messageSource = null;
	
	@Autowired
    private EntityManagerFactory entityManagerFactory;
	
	@Override
	public Object create(Object entity) throws ODataApplicationException {

		if(entity instanceof ProvinciaEdm) {
			
			ProvinciaEdm provinciaEdm = (ProvinciaEdm) entity;
			ProvinciaFrmDto provinciaFrmDto = new ProvinciaFrmDto(provinciaEdm);
			
			try {
				ValidatorUtil.validate(provinciaFrmDtoValidator, messageSource, provinciaFrmDto);
				ProvinciaEntity provinciaEntity = provinciaService.nuevo(provinciaFrmDto);
				return new ProvinciaEdm(provinciaEntity);
			} catch (Exception e) {
				String message = SQLExceptionParser.parse(e);
				throw new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
			}
		}
		
		throw new ODataApplicationException("LOS DATOS NO CORRESPONDEN A LA ENTIDAD PROVINCIA", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
	}

	@Override
	public Object update(Map<String, UriParameter> keyPredicateMap, Object entity, List<String> propertiesInJSON, boolean isPut) throws ODataApplicationException {

    	if(entity instanceof ProvinciaEdm) {
    		
    		ProvinciaEdm provincia = (ProvinciaEdm) entity;
    		ProvinciaFrmDto provinciaFrmDto;
    		
        	Integer provinciaID = Integer.valueOf( keyPredicateMap.get("id").getText() );
        	Integer paisID = Integer.valueOf( keyPredicateMap.get("paisId").getText() );
        	
    		if(isPut) {
    			provinciaFrmDto = new ProvinciaFrmDto(provincia);
    			provinciaFrmDto.setId(provinciaID);
    			provinciaFrmDto.setPaisID(paisID);
    		} else {
	    		ProvinciaEntity provinciaEntity = provinciaService.buscarPorID(paisID, provinciaID);
	    		
	    		if(provinciaEntity == null) {
	    			throw new ODataApplicationException(
	    					String.format("LA PROVINCIA CON ID (PAIS=%s,PROVINCIA=%s) NO EXITE", provincia.getPaisId(), provincia.getId()), HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
	    		}
	    		
	    		// *** CAMPO << NOMBRE >>
	    		
	    		if(propertiesInJSON.contains("nombre")) {
	    			provinciaEntity.setNombre(provincia.getNombre() == null || provincia.getNombre().trim().isEmpty() ? null : provincia.getNombre().trim().toUpperCase());
	    		}
	    		
	    		provinciaFrmDto = new ProvinciaFrmDto(provinciaEntity);
    		}
    		
			try {
				ValidatorUtil.validate(provinciaFrmDtoValidator, messageSource, provinciaFrmDto);
				return new ProvinciaEdm( provinciaService.actualizar(provinciaFrmDto) );
			} catch (Exception e) {
				String message = SQLExceptionParser.parse(e);
				throw new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
			}
    	}
    	
    	throw new ODataApplicationException("LOS DATOS NO CORRESPONDEN A LA ENTIDAD PROVINCIA", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
	}

	@Override
	public Object delete(Map<String, UriParameter> keyPredicateMap) throws ODataApplicationException {

    	Integer provinciaID = Integer.valueOf( keyPredicateMap.get("id").getText() );
    	Integer paisID = Integer.valueOf( keyPredicateMap.get("paisId").getText() );
    	
    	try {
			provinciaService.borrar(paisID, provinciaID);
		} catch (Exception e) {
			String message = SQLExceptionParser.parse(e);
			throw new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
		}
    	
    	return null;
	}

	@Override
	public String isSuitableFor() {
		return ENTITY_SET_NAME;
	}

	@Override
	public Object readFromKey(Map<String, UriParameter> keyPredicateMap, ExpandOption expandOption, SelectOption selectOption) throws ODataApplicationException {
		
		Integer provinciaID = Integer.valueOf( keyPredicateMap.get("id").getText() );
    	Integer paisID = Integer.valueOf( keyPredicateMap.get("paisId").getText() );
    	
    	ProvinciaEntity provinciaEntity = provinciaService.buscarPorID(paisID, provinciaID);
    	ProvinciaEdm provinciaEdm = provinciaEntity == null ? null : new ProvinciaEdm(provinciaEntity);
    	
    	return provinciaEdm;
	}

	@Override
	public Iterable<?> readAll(ExpandOption expandOption, FilterOption filterOption, OrderByOption orderByOption) throws ODataApplicationException {

		JPQLQuery query = new JPQLQueryBuilder()
			.setDistinct(true)
			.setClazz(ProvinciaEdm.class)
			.setExpandOption(expandOption)
			.setFilterOption(filterOption)
			.setOrderByOption(orderByOption)
			.build();
	
		List<ProvinciaEntity> provinciaEntities = JPQLQuery.execute(entityManagerFactory.createEntityManager(), query);
		List<ProvinciaEdm> provinciaEdms = provinciaEntities.stream().map(entity -> { return new ProvinciaEdm(entity); }).collect(Collectors.toList());
		
		return provinciaEdms;
	}
}
