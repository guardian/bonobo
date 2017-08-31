import com.gu.googleauth.UserIdentity
import play.api.mvc.Security.AuthenticatedRequest

package object controllers {

  type AuthReq[A] = AuthenticatedRequest[A, UserIdentity]

}