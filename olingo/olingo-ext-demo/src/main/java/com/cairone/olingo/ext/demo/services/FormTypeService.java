package com.cairone.olingo.ext.demo.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cairone.olingo.ext.demo.dtos.FormTypeFrmDto;
import com.cairone.olingo.ext.demo.entities.FormTypeEntity;
import com.cairone.olingo.ext.demo.exceptions.ServiceException;
import com.cairone.olingo.ext.demo.repositories.FormTypeRepository;

@Service
public class FormTypeService {

	@Autowired private FormTypeRepository formTypeRepository = null;

	@Transactional(readOnly=true)
	public FormTypeEntity findOne(Integer id) throws ServiceException {
		
		if(id == null) throw new ServiceException(ServiceException.MISSING_DATA, "ENTITY ID CAN NOT BE NULL");
		FormTypeEntity formTypeEntity = formTypeRepository.findOne(id);
		
		if(formTypeEntity == null) {
			throw new ServiceException(ServiceException.NOT_FOUND, String.format("COULD NOT BE FOUND AN ENTITY WITH ID %s", id));
		}
		
		return formTypeEntity;
	}

	@Transactional
	public FormTypeEntity save(FormTypeFrmDto formTypeFrmDto) throws ServiceException {
		
		FormTypeEntity formTypeEntity = new FormTypeEntity(formTypeFrmDto.getId(), formTypeFrmDto.getName());
		formTypeRepository.save(formTypeEntity);
		
		return formTypeEntity;
	}

	@Transactional
	public void delete(FormTypeEntity formTypeEntity) {
		formTypeRepository.delete(formTypeEntity);
	}
}
