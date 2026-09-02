package com.museotek.box.web.authorization;

import com.museotek.box.application.authorization.GetMyAuthorizationQuery;
import com.museotek.box.infrastructure.catalogue.CatalogueMeAuthorizationDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final GetMyAuthorizationQuery getMyAuthorizationQuery;

    public MeController(GetMyAuthorizationQuery getMyAuthorizationQuery) {
        this.getMyAuthorizationQuery = getMyAuthorizationQuery;
    }

    @GetMapping("/authorization")
    public MeAuthorizationResponse authorization() {
        CatalogueMeAuthorizationDto authorization = getMyAuthorizationQuery.execute();
        return MeAuthorizationResponse.from(authorization);
    }
}
